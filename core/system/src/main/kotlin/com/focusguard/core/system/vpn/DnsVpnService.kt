package com.focusguard.core.system.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.system.engine.ContentFilterEngine
import com.focusguard.core.system.engine.DnsFilterEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class DnsVpnService : android.net.VpnService() {
    @Inject lateinit var dnsFilterEngine: DnsFilterEngine

    @Inject lateinit var webBlockRuleRepository: WebBlockRuleRepository

    @Inject lateinit var contentFilterEngine: ContentFilterEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rulesCache = AtomicReference<List<WebBlockRule>>(emptyList())
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        tunInterface?.close()
    }

    override fun onRevoke() {
        stopVpn()
    }

    private fun startVpn() {
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            combine(
                webBlockRuleRepository.getWebBlockRules(),
                webBlockRuleRepository.getContentFilters(),
            ) { webRules, activeFilters ->
                webRules.filter { it.enabled } + contentFilterEngine.dnsRulesFor(activeFilters)
            }.collect { combinedRules ->
                rulesCache.set(combinedRules)
            }
        }

        // establish() can throw (e.g. SecurityException under another app's always-on/lockdown
        // VPN, or if consent is revoked between VpnService.prepare() and this call) as well as
        // return null on failure. startForeground() above has already posted the notification,
        // so an uncaught exception here would crash the service mid-start, leaving a misleading
        // "active" notification behind. Treat a thrown exception the same as a null result.
        val pfd =
            runCatching {
                Builder()
                    .addAddress(VPN_ADDRESS, 32)
                    .addDnsServer(DNS_INTERCEPT)
                    .addRoute(DNS_INTERCEPT, 32)
                    .setMtu(1500)
                    .setBlocking(false)
                    .setSession("FocusGuard")
                    .establish()
            }.onFailure { Log.e(TAG, "VPN establish() failed", it) }.getOrNull()

        if (pfd == null) {
            stopSelf()
            return
        }
        tunInterface = pfd

        serviceScope.launch { runDnsLoop(pfd) }
    }

    private fun stopVpn() {
        serviceScope.cancel()
        tunInterface?.close()
        tunInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runDnsLoop(pfd: ParcelFileDescriptor) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (currentCoroutineContext().isActive) {
            val len =
                withContext(Dispatchers.IO) {
                    runCatching { inStream.read(buffer) }.getOrElse { -1 }
                }
            if (len <= 0) {
                delay(5)
                continue
            }

            val response = handlePacket(buffer.copyOf(len))
            if (response != null) {
                withContext(Dispatchers.IO) {
                    runCatching { outStream.write(response) }
                }
            }
        }
    }

    private suspend fun handlePacket(packet: ByteArray): ByteArray? {
        val isIpv4 = packet.size >= 28 && (packet[0].toInt() and 0xFF) shr 4 == 4
        val isUdp = isIpv4 && (packet[9].toInt() and 0xFF) == 17
        if (!isUdp) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        // IHL can exceed the minimum 20-byte header when IP options are present — without this
        // check a header that long crosses the 28-byte floor validated above, and the field
        // reads below throw ArrayIndexOutOfBoundsException. That's uncaught here (it's before
        // runCatching), which silently kills the runDnsLoop coroutine and stops DNS forwarding
        // until the VPN service is restarted.
        if (packet.size < ihl + 8) return null
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val dnsPayload = packet.copyOfRange(ihl + 8, packet.size)

        return runCatching {
            val query = Message(dnsPayload)
            val question = query.question ?: return null
            val hostname = question.name.toString().trimEnd('.')

            if (dnsFilterEngine.shouldBlock(hostname, rulesCache.get())) {
                buildNxdomainResponse(packet, ihl, query)
            } else {
                forwardQuery(packet, ihl, dnsPayload)
            }
        }.getOrNull()
    }

    private fun buildNxdomainResponse(
        queryPacket: ByteArray,
        ihl: Int,
        query: Message,
    ): ByteArray {
        val response = Message(query.header.id)
        response.header.setFlag(Flags.QR.toInt())
        response.header.setFlag(Flags.RD.toInt())
        response.header.setRcode(Rcode.NXDOMAIN)
        query.question?.let { response.addRecord(it, Section.QUESTION) }
        return wrapInUdpIp(queryPacket, ihl, response.toWire())
    }

    private suspend fun forwardQuery(
        queryPacket: ByteArray,
        ihl: Int,
        dnsPayload: ByteArray,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 3_000
                    val upstream = InetAddress.getByName(UPSTREAM_DNS)
                    socket.send(DatagramPacket(dnsPayload, dnsPayload.size, upstream, 53))
                    val buf = ByteArray(4096)
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    wrapInUdpIp(queryPacket, ihl, resp.data.copyOf(resp.length))
                }
            }.getOrNull()
        }

    private fun wrapInUdpIp(
        queryPacket: ByteArray,
        ihl: Int,
        dnsResponse: ByteArray,
    ): ByteArray {
        val srcPort = ((queryPacket[ihl].toInt() and 0xFF) shl 8) or (queryPacket[ihl + 1].toInt() and 0xFF)
        val dstPort = ((queryPacket[ihl + 2].toInt() and 0xFF) shl 8) or (queryPacket[ihl + 3].toInt() and 0xFF)
        val srcIp = queryPacket.copyOfRange(12, 16)
        val dstIp = queryPacket.copyOfRange(16, 20)

        val udpLen = 8 + dnsResponse.size
        val totalLen = 20 + udpLen
        val out = ByteArray(totalLen)

        // IPv4 header (swap src/dst)
        out[0] = 0x45.toByte() // version=4, ihl=5
        out[2] = (totalLen shr 8).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        out[6] = 0x40.toByte() // don't fragment
        out[8] = 64 // TTL
        out[9] = 17 // protocol: UDP
        System.arraycopy(dstIp, 0, out, 12, 4) // src ← original dst
        System.arraycopy(srcIp, 0, out, 16, 4) // dst ← original src
        val checksum = ipChecksum(out)
        out[10] = (checksum shr 8).toByte()
        out[11] = (checksum and 0xFF).toByte()

        // UDP header (swap src/dst ports)
        out[20] = (dstPort shr 8).toByte() // src port ← 53
        out[21] = (dstPort and 0xFF).toByte()
        out[22] = (srcPort shr 8).toByte() // dst port ← ephemeral
        out[23] = (srcPort and 0xFF).toByte()
        out[24] = (udpLen shr 8).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        // UDP checksum left as 0 (optional for IPv4)

        System.arraycopy(dnsResponse, 0, out, 28, dnsResponse.size)
        return out
    }

    private fun ipChecksum(header: ByteArray): Int {
        var sum = 0
        for (i in 0 until 20 step 2) {
            if (i == 10) continue // skip checksum field
            sum += ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun buildNotification(): Notification {
        val channelId = "focusguard_vpn"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(channelId, "Website Blocking", NotificationManager.IMPORTANCE_LOW),
            )
        return Notification
            .Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("FocusGuard")
            .setContentText("Website blocking is active")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.focusguard.vpn.STOP"
        private const val TAG = "DnsVpnService"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val DNS_INTERCEPT = "10.111.222.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
    }
}
