package com.focusguard.feature.onboarding

import com.focusguard.core.domain.model.PermissionsHealth

enum class OnboardingStep {
    WELCOME,
    ACCESSIBILITY,
    USAGE_ACCESS,
    BATTERY,
    VPN,
    EXACT_ALARM,
    DONE,
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val health: PermissionsHealth =
        PermissionsHealth(
            accessibilityEnabled = false,
            vpnPrepared = false,
            usageStatsGranted = false,
            batteryOptimizationIgnored = false,
            exactAlarmAllowed = false,
        ),
    // Steps where the user has already tapped "Grant access" at least once.
    // Used to show "Continue anyway" so users are never permanently blocked.
    val attemptedSteps: Set<OnboardingStep> = emptySet(),
)

sealed interface OnboardingEffect {
    data object OpenAccessibilitySettings : OnboardingEffect

    data object OpenUsageAccessSettings : OnboardingEffect

    data object RequestBatteryOptimization : OnboardingEffect

    data object RequestVpnPrepare : OnboardingEffect

    data object OpenExactAlarmSettings : OnboardingEffect

    data object NavigateToMain : OnboardingEffect
}
