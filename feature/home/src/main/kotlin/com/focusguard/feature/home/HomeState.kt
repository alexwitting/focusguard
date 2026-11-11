package com.focusguard.feature.home

import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.model.SystemHealth
import java.time.LocalDate

data class HomeState(
    /** The date the dashboard is showing, from ClockProvider; null until the first load. */
    val today: LocalDate? = null,
    val dailyUsage: List<AppUsageSummary> = emptyList(),
    val weeklyTotals: List<DailyTotal> = emptyList(),
    val weeklyAppBreakdown: List<AppUsageSummary> = emptyList(),
    val blockedAttempts: Int = 0,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    // Assumed healthy until the first reading, so no warning flashes on load.
    val systemHealth: SystemHealth = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true),
) {
    /**
     * Today's screen time: the day's total from [weeklyTotals], which includes sub-minute use and
     * matches what the weekly average is built from. Falls back to the per-app sum.
     */
    val totalMinutesToday: Int
        get() = weeklyTotals.firstOrNull { it.date == today }?.totalMinutes ?: dailyUsage.sumOf { it.totalMinutes }
    val significantDailyUsage: List<AppUsageSummary>
        get() = dailyUsage.filter { it.totalMinutes >= MIN_LISTED_MINUTES }

    /** Weekly breakdown minus sub-minute entries, so "Top apps this week" never lists "0m". */
    val significantWeeklyUsage: List<AppUsageSummary>
        get() = weeklyAppBreakdown.filter { it.totalMinutes >= MIN_LISTED_MINUTES }

    private companion object {
        /** Same one-minute cutoff as UsageDao and the Block tab's "Xm today". */
        const val MIN_LISTED_MINUTES = 1
    }
}
