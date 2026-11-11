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
    // Steps where the user has tapped "Grant access"; these offer "Continue anyway".
    val attemptedSteps: Set<OnboardingStep> = emptySet(),
) {
    /** Whether [step]'s permission is granted. WELCOME and DONE have none, so they're always true. */
    fun isGranted(step: OnboardingStep): Boolean =
        when (step) {
            OnboardingStep.ACCESSIBILITY -> health.accessibilityEnabled
            OnboardingStep.USAGE_ACCESS -> health.usageStatsGranted
            OnboardingStep.BATTERY -> health.batteryOptimizationIgnored
            OnboardingStep.VPN -> health.vpnPrepared
            OnboardingStep.EXACT_ALARM -> health.exactAlarmAllowed
            OnboardingStep.WELCOME, OnboardingStep.DONE -> true
        }
}

sealed interface OnboardingEffect {
    data object OpenAccessibilitySettings : OnboardingEffect

    data object OpenUsageAccessSettings : OnboardingEffect

    data object RequestBatteryOptimization : OnboardingEffect

    data object RequestVpnPrepare : OnboardingEffect

    data object OpenExactAlarmSettings : OnboardingEffect

    /**
     * FocusGuard's App info page. On Android 13+ a sideloaded app's accessibility switch stays
     * greyed out until the user picks "Allow restricted settings" from its overflow menu.
     */
    data object OpenAppDetailsSettings : OnboardingEffect

    data object NavigateToMain : OnboardingEffect
}
