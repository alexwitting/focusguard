package com.focusguard.core.data.repository

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `Build.VERSION.SDK_INT` is 0 in plain JVM tests, so exact alarms always read as allowed.
 * VPN detection scans `allNetworks`, which the tests stub directly.
 */
@Suppress("DEPRECATION")
class SystemHealthRepositoryImplTest {
    private val context = mockk<Context>()
    private val accessibilityManager = mockk<AccessibilityManager>()
    private val connectivityManager = mockk<ConnectivityManager>()
    private val appOpsManager = mockk<AppOpsManager>()
    private val powerManager = mockk<PowerManager>()

    @BeforeEach
    fun setUp() {
        every { context.packageName } returns PACKAGE_NAME
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns accessibilityManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager

        every { connectivityManager.allNetworks } returns emptyArray()
        every { powerManager.isIgnoringBatteryOptimizations(PACKAGE_NAME) } returns false

        mockkStatic(Process::class)
        every { Process.myUid() } returns 1000
        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 1000, PACKAGE_NAME)
        } returns AppOpsManager.MODE_ERRORED

        mockkStatic(VpnService::class)
        every { VpnService.prepare(context) } returns null

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Process::class)
        unmockkStatic(VpnService::class)
        unmockkStatic(Log::class)
    }

    private fun enabledAccessibilityService(): AccessibilityServiceInfo =
        mockk<AccessibilityServiceInfo> {
            every { resolveInfo } returns
                ResolveInfo().apply {
                    serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME }
                }
        }

    private fun networkWithVpnTransport(hasVpn: Boolean): Network {
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns hasVpn
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        return network
    }

    @Test
    fun `checkSystemHealthNow reports inactive when no service is running`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                emptyList()

            val repository = SystemHealthRepositoryImpl(context)
            val health = repository.checkSystemHealthNow()

            assertFalse(health.accessibilityServiceActive)
            assertFalse(health.vpnServiceActive)
        }

    @Test
    fun `checkSystemHealthNow reports active when our accessibility service is enabled and a VPN network is present`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                listOf(enabledAccessibilityService())
            every { connectivityManager.allNetworks } returns arrayOf(networkWithVpnTransport(hasVpn = true))

            val repository = SystemHealthRepositoryImpl(context)
            val health = repository.checkSystemHealthNow()

            assertTrue(health.accessibilityServiceActive)
            assertTrue(health.vpnServiceActive)
            assertTrue(health.isBlockingActive)
        }

    @Test
    fun `checkSystemHealthNow reports vpn inactive again once the VPN network is gone from allNetworks`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                emptyList()
            val repository = SystemHealthRepositoryImpl(context)

            every { connectivityManager.allNetworks } returns arrayOf(networkWithVpnTransport(hasVpn = true))
            assertTrue(repository.checkSystemHealthNow().vpnServiceActive)

            every { connectivityManager.allNetworks } returns emptyArray()
            assertFalse(repository.checkSystemHealthNow().vpnServiceActive)
        }

    @Test
    fun `checkSystemHealthNow reports active when a VPN tunnel was already established before this call`() =
        runTest {
            // Detected by scanning current networks; no callback involved.
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                emptyList()
            every { connectivityManager.allNetworks } returns
                arrayOf(networkWithVpnTransport(hasVpn = false), networkWithVpnTransport(hasVpn = true))

            val repository = SystemHealthRepositoryImpl(context)

            assertTrue(repository.checkSystemHealthNow().vpnServiceActive)
        }

    @Test
    fun `checkPermissionsNow reports each permission independently`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                listOf(enabledAccessibilityService())
            every {
                appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 1000, PACKAGE_NAME)
            } returns AppOpsManager.MODE_ALLOWED
            every { powerManager.isIgnoringBatteryOptimizations(PACKAGE_NAME) } returns true
            every { VpnService.prepare(context) } returns null

            val repository = SystemHealthRepositoryImpl(context)
            val health = repository.checkPermissionsNow()

            assertTrue(health.accessibilityEnabled)
            assertTrue(health.vpnPrepared)
            assertTrue(health.usageStatsGranted)
            assertTrue(health.batteryOptimizationIgnored)
            assertTrue(health.exactAlarmAllowed)
        }

    @Test
    fun `checkPermissionsNow reports vpn not prepared when VpnService prepare returns an intent`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                emptyList()
            every { VpnService.prepare(context) } returns mockk()

            val repository = SystemHealthRepositoryImpl(context)
            val health = repository.checkPermissionsNow()

            assertFalse(health.vpnPrepared)
        }

    @Test
    fun `observeSystemHealth emits a health snapshot`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                listOf(enabledAccessibilityService())

            val repository = SystemHealthRepositoryImpl(context)

            assertTrue(repository.observeSystemHealth().first().accessibilityServiceActive)
        }

    @Test
    fun `observePermissionsHealth emits a permissions snapshot`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
                listOf(enabledAccessibilityService())

            val repository = SystemHealthRepositoryImpl(context)

            assertTrue(repository.observePermissionsHealth().first().accessibilityEnabled)
        }

    @Test
    fun `observeSystemHealth survives a transient failure and resumes emitting on the next poll`() =
        runTest {
            // The first check throws; the poll catches it and emits on the next tick.
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } throws
                RuntimeException("transient") andThen listOf(enabledAccessibilityService())

            val repository = SystemHealthRepositoryImpl(context)

            assertTrue(repository.observeSystemHealth().first().accessibilityServiceActive)
        }

    @Test
    fun `observePermissionsHealth survives a transient failure and resumes emitting on the next poll`() =
        runTest {
            every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } throws
                RuntimeException("transient") andThen listOf(enabledAccessibilityService())

            val repository = SystemHealthRepositoryImpl(context)

            assertTrue(repository.observePermissionsHealth().first().accessibilityEnabled)
        }

    private companion object {
        const val PACKAGE_NAME = "com.focusguard"
    }
}
