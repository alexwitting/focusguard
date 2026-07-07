package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemHealthTest {
    @Test
    fun `isFullyOperational is true when both services are active`() {
        val health = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true)

        assertTrue(health.isFullyOperational)
    }

    @Test
    fun `isFullyOperational is false when the accessibility service is inactive`() {
        val health = SystemHealth(accessibilityServiceActive = false, vpnServiceActive = true)

        assertFalse(health.isFullyOperational)
    }

    @Test
    fun `isFullyOperational is false when the vpn service is inactive`() {
        val health = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false)

        assertFalse(health.isFullyOperational)
    }
}
