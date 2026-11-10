package com.focusguard.core.domain.model

data class SystemHealth(
    val accessibilityServiceActive: Boolean,
    val vpnServiceActive: Boolean,
) {
    /**
     * Whether blocking is actually working. Only the accessibility service is required: it
     * blocks apps, content feeds and (via the browser scan) websites. The DNS VPN is optional
     * extra website coverage, so it being off is not a problem worth warning about.
     */
    val isBlockingActive: Boolean get() = accessibilityServiceActive
}
