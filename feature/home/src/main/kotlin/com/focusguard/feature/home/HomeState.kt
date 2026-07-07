package com.focusguard.feature.home

import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.model.SystemHealth

data class HomeState(
    val dailyUsage: List<AppUsageSummary> = emptyList(),
    val weeklyTotals: List<DailyTotal> = emptyList(),
    val weeklyAppBreakdown: List<AppUsageSummary> = emptyList(),
    val blockedAttempts: Int = 0,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    // Optimistic defaults: this only reflects a real reading once observeSystemHealth() emits,
    // so a momentarily-disabled service never flashes a false warning before that first emission.
    val systemHealth: SystemHealth = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true),
) {
    val totalMinutesToday: Int get() = dailyUsage.sumOf { it.totalMinutes }
    val significantDailyUsage: List<AppUsageSummary>
        get() = dailyUsage.filter { it.totalMinutes > 1 }
}
