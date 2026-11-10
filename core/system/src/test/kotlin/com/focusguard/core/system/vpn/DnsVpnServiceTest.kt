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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Type
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * DnsVpnService's only meaningfully unit-testable surface is packet parsing/decision logic
 * (handlePacket, ipChecksum, wrapInUdpIp) — VpnService.Builder().establish() needs a real
 * OS-level tun interface and DatagramSocket-based forwardQuery() needs a real network, neither
 * available under Robolectric. Both are private, hence kotlin-reflect (handlePacket is also
 * `suspend`, requiring `callSuspend` rather than plain java.lang.reflect).
 *
 * JUnit4 + Robolectric's own runner, matching FocusGuardAccessibilityServiceTest — see that
 * file's header comment for why (the community JUnit5 bridge showed cross-test isolation bugs).
 */
@RunWith(RobolectricTestRunner::class)
class DnsVpnServiceTest {
    private val webBlockRuleRepository = FakeWebBlockRuleRepository()
    private lateinit var service: DnsVpnService

    @Before
    fun setUp() {
        // .get() only — .create() runs Hilt's generated injection path, which needs a real
        // @HiltAndroidApp test Application (see FocusGuardAccessibilityServiceTest for the same
        // reasoning). Fields are public (no visibility modifier) so they're assigned directly.
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

    /** Builds a minimal IPv4/UDP/DNS-query packet, the shape `handlePacket` expects. */
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

    private fun handlePacket(packet: ByteArray): ByteArray? =
        runBlocking {
            val function = DnsVpnService::class.declaredFunctions.first { it.name == "handlePacket" }
            function.isAccessible = true
            function.callSuspend(service, packet) as ByteArray?
        }

    @Test
    fun `handlePacket returns an NXDOMAIN response for a blocked hostname`() =
        runTest {
            setRulesCache(listOf(WebBlockRule(domain = "blocked.example", enabled = true)))
            val packet = buildDnsQueryPacket("blocked.example", srcPort = 40_000)

            val response = handlePacket(packet)

            assertNotNull(response)
            val dnsResponse = Message(response!!.copyOfRange(28, response.size))
            assertEquals(Rcode.NXDOMAIN, dnsResponse.header.rcode)

            // wrapInUdpIp swaps src/dst so the response routes back to the original requester:
            // response src port is the query's dst (53), response dst port is the query's src.
            val responseSrcPort = ((response[20].toInt() and 0xFF) shl 8) or (response[21].toInt() and 0xFF)
            val responseDstPort = ((response[22].toInt() and 0xFF) shl 8) or (response[23].toInt() and 0xFF)
            assertEquals(53, responseSrcPort)
            assertEquals(40_000, responseDstPort)
        }

    @Test
    fun `handlePacket ignores packets shorter than a minimum IPv4+UDP header`() {
        val response = handlePacket(ByteArray(10))

        assertNull(response)
    }

    @Test
    fun `handlePacket ignores non-IPv4 packets`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[0] = 0x60 // version 6

        val response = handlePacket(packet)

        assertNull(response)
    }

    @Test
    fun `handlePacket ignores non-UDP packets`() {
        val packet = buildDnsQueryPacket("example.com")
        packet[9] = 6 // TCP

        val response = handlePacket(packet)

        assertNull(response)
    }

    @Test
    fun `handlePacket ignores packets not addressed to port 53`() {
        val dnsQuery = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN)).toWire()
        val packet = ByteArray(28 + dnsQuery.size)
        packet[0] = 0x45
        packet[9] = 17
        packet[22] = 0
        packet[23] = 80 // dst port 80, not 53
        System.arraycopy(dnsQuery, 0, packet, 28, dnsQuery.size)

        val response = handlePacket(packet)

        assertNull(response)
    }

    @Test
    fun `handlePacket ignores a packet whose declared IHL exceeds the packet's actual size`() {
        val packet = ByteArray(28)
        packet[0] = 0x4F // version 4, IHL 15 (60-byte header) — far beyond this 28-byte packet
        packet[9] = 17

        val response = handlePacket(packet)

        assertNull(response)
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
    fun `handlePacket ignores a DNS payload it can't parse`() {
        val packet = buildDnsQueryPacket("example.com")
        // Corrupt the DNS section so dnsjava can't parse it.
        for (i in 28 until packet.size) packet[i] = 0xFF.toByte()

        assertNull(handlePacket(packet))
    }

    @Test
    fun `handlePacket ignores a DNS message with no question`() {
        val noQuestion = Message().toWire()
        val packet = buildDnsQueryPacket("example.com").copyOf(28 + noQuestion.size)
        System.arraycopy(noQuestion, 0, packet, 28, noQuestion.size)

        assertNull(handlePacket(packet))
    }

    @Test
    fun `onStartCommand with ACTION_STOP tears the service down without starting a VPN`() {
        val intent = android.content.Intent(DnsVpnService.ACTION_STOP)

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    // forwardQuery() is not practically unit-testable: it calls protect(DatagramSocket), but
    // ShadowVpnService only shadows protect(java.net.Socket) — the unshadowed overload falls
    // through to real framework code that needs a live VPN binder. Covered instead by the
    // real-device instrumented test.

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
