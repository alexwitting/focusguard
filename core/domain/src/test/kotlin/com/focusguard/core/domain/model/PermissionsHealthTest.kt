package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PermissionsHealthTest {
    private val allGranted =
        PermissionsHealth(
            accessibilityEnabled = true,
            vpnPrepared = true,
            usageStatsGranted = true,
            batteryOptimizationIgnored = true,
            exactAlarmAllowed = true,
        )

    @Test
    fun `allGranted is true when every permission is granted`() {
        assertTrue(allGranted.allGranted)
    }

    @Test
    fun `allGranted is false when accessibility is missing`() {
        assertFalse(allGranted.copy(accessibilityEnabled = false).allGranted)
    }

    @Test
    fun `allGranted is false when vpn is not prepared`() {
        assertFalse(allGranted.copy(vpnPrepared = false).allGranted)
    }

    @Test
    fun `allGranted is false when usage stats access is missing`() {
        assertFalse(allGranted.copy(usageStatsGranted = false).allGranted)
    }

    @Test
    fun `allGranted is false when battery optimization is not ignored`() {
        assertFalse(allGranted.copy(batteryOptimizationIgnored = false).allGranted)
    }

    @Test
    fun `allGranted is false when exact alarms are not allowed`() {
        assertFalse(allGranted.copy(exactAlarmAllowed = false).allGranted)
    }
}
