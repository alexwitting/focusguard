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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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

    /** Owns one running tunnel's coroutines, so stopping it leaves the service scope intact. */
    private var sessionJob: Job? = null

    /** Caps in-flight upstream lookups; each can wait up to the socket timeout. */
    private val inFlightQueries = Semaphore(MAX_IN_FLIGHT_QUERIES)

    /** One writer at a time on the tunnel's output stream. */
    private val writeLock = Mutex()

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
        // Already running: start requests arrive again for Settings' "Start" and sticky restarts.
        if (tunInterface != null) return

        startForeground(NOTIFICATION_ID, buildNotification())

        // establish() can throw (another app's always-on VPN, consent revoked) or return null;
        // treat both as failure.
        val pfd =
            runCatching {
                Builder()
                    .addAddress(VPN_ADDRESS, 32)
                    .addDnsServer(DNS_INTERCEPT)
                    .addRoute(DNS_INTERCEPT, 32)
                    .setMtu(1500)
                    // Blocking reads: the loop sleeps until a packet arrives.
                    .setBlocking(true)
                    .setSession("FocusGuard")
                    .establish()
            }.onFailure { Log.e(TAG, "VPN establish() failed", it) }.getOrNull()

        if (pfd == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        tunInterface = pfd

        val session = SupervisorJob(serviceScope.coroutineContext[Job])
        sessionJob = session
        val sessionScope = CoroutineScope(serviceScope.coroutineContext + session)
        sessionScope.launch {
            combine(
                webBlockRuleRepository.getWebBlockRules(),
                webBlockRuleRepository.getContentFilters(),
            ) { webRules, activeFilters ->
                webRules.filter { it.enabled } + contentFilterEngine.dnsRulesFor(activeFilters)
            }.catch { Log.e(TAG, "Loading block rules failed", it) }
                .collect { combinedRules -> rulesCache.set(combinedRules) }
        }
        sessionScope.launch { runDnsLoop(pfd, sessionScope) }
    }

    private fun stopVpn() {
        sessionJob?.cancel()
        sessionJob = null
        // Closing the fd also unblocks the read loop, which exits on the resulting IOException.
        tunInterface?.close()
        tunInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Reads packets until the tunnel closes. Each query is answered on its own coroutine so a
     * slow upstream lookup doesn't hold up the others.
     */
    private suspend fun runDnsLoop(
        pfd: ParcelFileDescriptor,
        scope: CoroutineScope,
    ) {
        val inStream = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)

        while (currentCoroutineContext().isActive) {
            val len =
                try {
                    inStream.read(buffer)
                } catch (e: IOException) {
                    // Tunnel closed by stopVpn or onRevoke.
                    Log.d(TAG, "Tunnel read ended", e)
                    return
                }
            if (len < 0) return // end of stream: the tunnel is gone
            if (len == 0) continue

            val packet = buffer.copyOf(len)
            inFlightQueries.acquire()
            scope.launch {
                try {
                    val response = handlePacket(packet) ?: return@launch
                    writeLock.withLock { runCatching { outStream.write(response) } }
                } finally {
                    inFlightQueries.release()
                }
            }
        }
    }

    private suspend fun handlePacket(packet: ByteArray): ByteArray? {
        val isIpv4 = packet.size >= 28 && (packet[0].toInt() and 0xFF) shr 4 == 4
        val isUdp = isIpv4 && (packet[9].toInt() and 0xFF) == 17
        if (!isUdp) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        // IP options can make the header longer than the 28-byte minimum checked above.
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
    ): ByteArray? = upstream(dnsPayload)?.let { wrapInUdpIp(queryPacket, ihl, it) }

    /** Sends a DNS query to the upstream resolver and returns its reply, or null on failure. */
    internal var upstream: suspend (ByteArray) -> ByteArray? = ::queryUpstream

    private suspend fun queryUpstream(dnsPayload: ByteArray): ByteArray? =
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
                    resp.data.copyOf(resp.length)
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
                NotificationChannel(channelId, "Website blocking", NotificationManager.IMPORTANCE_LOW),
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
        private const val MAX_PACKET_SIZE = 32767
        private const val MAX_IN_FLIGHT_QUERIES = 32
    }
}
