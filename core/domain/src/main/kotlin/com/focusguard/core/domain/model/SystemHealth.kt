package com.focusguard.core.domain.model

data class SystemHealth(
    val accessibilityServiceActive: Boolean,
    val vpnServiceActive: Boolean,
) {
    /**
     * Whether blocking works: only the accessibility service is required. The DNS VPN is optional
     * extra website coverage.
     */
    val isBlockingActive: Boolean get() = accessibilityServiceActive
}
