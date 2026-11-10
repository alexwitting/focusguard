package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemHealthTest {
    @Test
    fun `blocking is active when the accessibility service is running`() {
        assertTrue(SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true).isBlockingActive)
    }

    @Test
    fun `the optional VPN being off does not count as blocking being down`() {
        assertTrue(SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false).isBlockingActive)
    }

    @Test
    fun `blocking is down when the accessibility service is off`() {
        assertFalse(SystemHealth(accessibilityServiceActive = false, vpnServiceActive = true).isBlockingActive)
    }
}
