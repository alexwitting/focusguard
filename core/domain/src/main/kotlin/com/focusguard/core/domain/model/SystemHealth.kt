package com.focusguard.core.domain.model

data class SystemHealth(
    val accessibilityServiceActive: Boolean,
    val vpnServiceActive: Boolean,
) {
    val isFullyOperational: Boolean get() = accessibilityServiceActive && vpnServiceActive
}
