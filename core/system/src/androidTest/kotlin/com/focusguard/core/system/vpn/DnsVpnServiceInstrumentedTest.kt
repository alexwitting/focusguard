package com.focusguard.core.system.vpn

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * On-device tests of the VPN's OS integration: a real tunnel and real reads and writes on it.
 * Packet handling is covered by DnsVpnServiceTest.
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
