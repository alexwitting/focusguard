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
    // Optimistic defaults: this only reflects a real reading once observeSystemHealth() emits,
    // so a momentarily-disabled service never flashes a false warning before that first emission.
    val systemHealth: SystemHealth = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true),
) {
    /**
     * Today's screen time. Prefers the day's total from [weeklyTotals], which sums every session
     * — the per-app list leaves out apps used for under a minute, so summing it undercounts, and
     * the weekly average this number is compared against is built from the same daily totals.
     */
    val totalMinutesToday: Int
        get() = weeklyTotals.firstOrNull { it.date == today }?.totalMinutes ?: dailyUsage.sumOf { it.totalMinutes }
    val significantDailyUsage: List<AppUsageSummary>
        get() = dailyUsage.filter { it.totalMinutes >= MIN_LISTED_MINUTES }

    /** Weekly breakdown minus sub-minute entries, so "Top apps this week" never lists "0m". */
    val significantWeeklyUsage: List<AppUsageSummary>
        get() = weeklyAppBreakdown.filter { it.totalMinutes >= MIN_LISTED_MINUTES }

    private companion object {
        /**
         * Matches UsageDao, which already drops apps under a minute; this guards the UI against
         * any other source of 0-minute rows. Same cutoff as the Block tab's "Xm today".
         */
        const val MIN_LISTED_MINUTES = 1
    }
}
