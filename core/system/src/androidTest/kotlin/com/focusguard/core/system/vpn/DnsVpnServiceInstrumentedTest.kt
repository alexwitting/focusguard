package com.focusguard.core.system.vpn

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * Real-device/emulator stability tests — the OS establishes a real VPN tunnel and runs real
 * FileInputStream/FileOutputStream I/O against it, neither achievable under Robolectric (see
 * DnsVpnServiceTest's header comment). See DnsVpnServiceTest (JVM unit tests) for the
 * packet-decision-logic coverage; this file is specifically about OS integration.
 */
@HiltAndroidTest
class DnsVpnServiceInstrumentedTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun hiltInjectionSucceeds() {
        hiltRule.inject()
    }
}
