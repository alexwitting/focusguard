package com.focusguard.core.domain.model

data class PermissionsHealth(
    val accessibilityEnabled: Boolean,
    val vpnPrepared: Boolean,
    val usageStatsGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val exactAlarmAllowed: Boolean,
) {
    val allGranted: Boolean get() =
        accessibilityEnabled &&
            vpnPrepared &&
            usageStatsGranted &&
            batteryOptimizationIgnored &&
            exactAlarmAllowed
}
