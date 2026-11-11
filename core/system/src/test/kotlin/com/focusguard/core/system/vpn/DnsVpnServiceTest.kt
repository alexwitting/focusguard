package com.focusguard.core.system.vpn

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.system.engine.ContentFilterEngine
import com.focusguard.core.system.engine.DnsFilterEngine
import com.focusguard.core.testing.FakeWebBlockRuleRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Type
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Tests DnsVpnService's packet handling (private, reached through kotlin-reflect). Opening a real
 * tunnel and forwarding to a real network aren't possible under Robolectric; the instrumented
 * test covers those. Uses Robolectric's JUnit4 runner.
 */
@RunWith(RobolectricTestRunner::class)
class DnsVpnServiceTest {
    private val webBlockRuleRepository = FakeWebBlockRuleRepository()
    private lateinit var service: DnsVpnService

    @Before
    fun setUp() {
        // get(), not create(): create() runs Hilt's injection, which needs a Hilt test app.
        service =
            Robolectric.buildService(DnsVpnService::class.java).get().apply {
                dnsFilterEngine = DnsFilterEngine()
                webBlockRuleRepository = this@DnsVpnServiceTest.webBlockRuleRepository
                contentFilterEngine = ContentFilterEngine()
            }
    }

    private fun setRulesCache(rules: List<WebBlockRule>) {
        val field = DnsVpnService::class.java.getDeclaredField("rulesCache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(service) as AtomicReference<List<WebBlockRule>>).set(rules)
    }

    private fun setTunInterface(pfd: ParcelFileDescriptor?) {
        val field = DnsVpnService::class.java.getDeclaredField("tunInterface")
        field.isAccessible = true
        field.set(service, pfd)
    }

    private fun getTunInterface(): ParcelFileDescriptor? {
        val field = DnsVpnService::class.java.getDeclaredField("tunInterface")
        field.isAccessible = true
        return field.get(service) as ParcelFileDescriptor?
    }

    /** Builds a minimal IPv4/UDP/DNS-query packet, the shape the tunnel reads. */
    private fun buildDnsQueryPacket(
        hostname: String,
        srcPort: Int = 12_345,
    ): ByteArray {
        val dnsQuery = Message.newQuery(Record.newRecord(Name.fromString("$hostname."), Type.A, DClass.IN)).toWire()
        val totalLen = 28 + dnsQuery.size
        val packet = ByteArray(totalLen)
        packet[0] = 0x45 // version 4, IHL 5 (20-byte header, no options)
        packet[9] = 17 // protocol: UDP
        packet[20] = (srcPort shr 8).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = (53 shr 8).toByte() // dst port 53
        packet[23] = (53 and 0xFF).toByte()
        val udpLen = 8 + dnsQuery.size
        packet[24] = (udpLen shr 8).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(dnsQuery, 0, packet, 28, dnsQuery.size)
        return packet
    }

    @Test
    fun `a blocked lookup's reply goes back to the asking port`() =
        runTest {
            setRulesCache(listOf(WebBlockRule(domain = "blocked.example", enabled = true)))
            val packet = buildDnsQueryPacket("blocked.example", srcPort = 40_000)

            val response = runTunnel(packet)

            val dnsResponse = Message(response.copyOfRange(28, response.size))
            assertEquals(Rcode.NXDOMAIN, dnsResponse.header.rcode)

            // wrapInUdpIp swaps src/dst so the response routes back to the original requester:
            // response src port is the query's dst (53), response dst port is the query's src.
            val responseSrcPort = ((response[20].toInt() and 0xFF) shl 8) or (response[21].toInt() and 0xFF)
            val responseDstPort = ((response[22].toInt() and 0xFF) shl 8) or (response[23].toInt() and 0xFF)
            assertEquals(53, responseSrcPort)
            assertEquals(40_000, responseDstPort)
        }

    @Test
    fun `the tunnel ignores packets shorter than a minimum IPv4+UDP header`() {
        val response = runTunnel(ByteArray(10))

        assertEquals(0, response.size)
    }

    @Test
    fun `the tunnel ignores non-IPv4 packets`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[0] = 0x60 // version 6

        val response = runTunnel(packet)

        assertEquals(0, response.size)
    }

    @Test
    fun `the tunnel ignores non-UDP packets`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[9] = 6 // TCP

        val response = runTunnel(packet)

        assertEquals(0, response.size)
    }

    @Test
    fun `the tunnel ignores packets not addressed to port 53`() {
        val dnsQuery = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN)).toWire()
        val packet = ByteArray(28 + dnsQuery.size)
        packet[0] = 0x45
        packet[9] = 17
        packet[22] = 0
        packet[23] = 80 // dst port 80, not 53
        System.arraycopy(dnsQuery, 0, packet, 28, dnsQuery.size)

        val response = runTunnel(packet)

        assertEquals(0, response.size)
    }

    @Test
    fun `the tunnel ignores a packet whose declared IHL exceeds the packet's actual size`() {
        val packet = ByteArray(28)
        packet[0] = 0x4F // version 4, IHL 15 (60-byte header) — far beyond this 28-byte packet
        packet[9] = 17

        val response = runTunnel(packet)

        assertEquals(0, response.size)
    }

    @Test
    fun `a second start command while the tunnel is up doesn't open another one`() {
        val existing = mockk<ParcelFileDescriptor>(relaxed = true)
        setTunInterface(existing)

        val result = service.onStartCommand(Intent(), 0, 1)

        assertEquals(android.app.Service.START_STICKY, result)
        assertSame(existing, getTunInterface())
    }

    @Test
    fun `a start command that can't establish the tunnel stops the service`() {
        setTunInterface(null)

        service.onStartCommand(Intent(), 0, 1)

        assertNull(getTunInterface())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    /** Feeds [packets] through the tunnel read loop until end of stream; returns what it wrote back. */
    private fun runTunnel(vararg packets: ByteArray): ByteArray {
        val input = packets.fold(ByteArray(0)) { acc, p -> acc + p }
        val tunnel = File.createTempFile("tun", ".bin").apply { writeBytes(input) }
        val pfd = ParcelFileDescriptor.open(tunnel, ParcelFileDescriptor.MODE_READ_WRITE)
        runBlocking {
            val loop = DnsVpnService::class.declaredFunctions.first { it.name == "runDnsLoop" }
            loop.isAccessible = true
            withTimeout(5_000) { loop.callSuspend(service, pfd, this) }
        }
        pfd.close()
        return tunnel.readBytes().copyOfRange(input.size, tunnel.length().toInt()).also { tunnel.delete() }
    }

    @Test
    fun `the tunnel answers a blocked lookup with NXDOMAIN without asking upstream`() {
        setRulesCache(listOf(WebBlockRule(domain = "blocked.example", enabled = true)))
        var upstreamCalls = 0
        service.upstream = {
            upstreamCalls++
            null
        }

        val reply = runTunnel(buildDnsQueryPacket("blocked.example"))

        assertEquals(Rcode.NXDOMAIN, Message(reply.copyOfRange(28, reply.size)).header.rcode)
        assertEquals(0, upstreamCalls)
    }

    @Test
    fun `the tunnel forwards an allowed lookup upstream and relays the answer back to the asker`() {
        setRulesCache(listOf(WebBlockRule(domain = "blocked.example", enabled = true)))
        val query = buildDnsQueryPacket("allowed.example", srcPort = 41_000)
        var forwarded: ByteArray? = null
        service.upstream = { payload ->
            forwarded = payload
            Message(payload).apply { header.setFlag(Flags.QR.toInt()) }.toWire()
        }

        val reply = runTunnel(query)

        assertTrue(forwarded!!.contentEquals(query.copyOfRange(28, query.size)))
        val answer = Message(reply.copyOfRange(28, reply.size))
        assertEquals(Rcode.NOERROR, answer.header.rcode)
        assertEquals("allowed.example.", answer.question.name.toString())
        val dstPort = ((reply[22].toInt() and 0xFF) shl 8) or (reply[23].toInt() and 0xFF)
        assertEquals(41_000, dstPort)
    }

    @Test
    fun `a lookup the upstream resolver doesn't answer gets no reply`() {
        service.upstream = { null }

        assertEquals(0, runTunnel(buildDnsQueryPacket("allowed.example")).size)
    }

    @Test
    fun `the tunnel ignores a DNS payload it can't parse`() {
        val packet = buildDnsQueryPacket("example.com")
        // Corrupt the DNS section so dnsjava can't parse it.
        for (i in 28 until packet.size) packet[i] = 0xFF.toByte()

        assertEquals(0, runTunnel(packet).size)
    }

    @Test
    fun `the tunnel ignores a DNS message with no question`() {
        val noQuestion = Message().toWire()
        val packet = buildDnsQueryPacket("example.com").copyOf(28 + noQuestion.size)
        System.arraycopy(noQuestion, 0, packet, 28, noQuestion.size)

        assertEquals(0, runTunnel(packet).size)
    }

    @Test
    fun `onStartCommand with ACTION_STOP tears the service down without starting a VPN`() {
        val intent = android.content.Intent(DnsVpnService.ACTION_STOP)

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    @Test
    fun `onDestroy cancels the scope and closes a non-null tunInterface`() {
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        setTunInterface(pfd)

        service.onDestroy()

        verify(exactly = 1) { pfd.close() }
    }

    @Test
    fun `onDestroy with no tunInterface does not throw`() {
        setTunInterface(null)

        service.onDestroy()
    }

    @Test
    fun `onRevoke tears the VPN down, closing tunInterface and clearing it`() {
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        setTunInterface(pfd)

        service.onRevoke()

        verify(exactly = 1) { pfd.close() }
        assertNull(getTunInterface())
    }

    @Test
    fun `buildNotification creates the VPN notification channel and an ongoing notification`() {
        val method = DnsVpnService::class.java.getDeclaredMethod("buildNotification")
        method.isAccessible = true

        val notification = method.invoke(service) as android.app.Notification

        val notificationManager =
            service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(shadowOf(notificationManager).notificationChannels.isNotEmpty())
        assertEquals("FocusGuard", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }
}
