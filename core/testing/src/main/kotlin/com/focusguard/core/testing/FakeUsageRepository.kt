package com.focusguard.core.testing

import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeUsageRepository : UsageRepository {
    private val _dailyUsage = MutableStateFlow<Map<LocalDate, List<AppUsageSummary>>>(emptyMap())
    private val _weeklyTotals = MutableStateFlow<List<DailyTotal>>(emptyList())
    private val _weeklyAppBreakdown = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    private val _blockedAttempts = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    private val _todayMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _recordedSessions = mutableListOf<AppUsageRecord>()

    val recordedSessions: List<AppUsageRecord> get() = _recordedSessions

    fun setTodayUsageMinutes(
        packageName: String,
        minutes: Int,
    ) {
        _todayMinutes.value = _todayMinutes.value + (packageName to minutes)
    }

    fun setDailyUsage(
        date: LocalDate,
        vararg summaries: AppUsageSummary,
    ) {
        _dailyUsage.value = _dailyUsage.value + (date to summaries.toList())
    }

    fun setWeeklyTotals(vararg totals: DailyTotal) {
        _weeklyTotals.value = totals.toList()
    }

    fun setWeeklyAppBreakdown(vararg summaries: AppUsageSummary) {
        _weeklyAppBreakdown.value = summaries.toList()
    }

    fun setBlockedAttempts(
        date: LocalDate,
        count: Int,
    ) {
        _blockedAttempts.value = _blockedAttempts.value + (date to count)
    }

    override fun getDailyUsage(date: LocalDate): Flow<List<AppUsageSummary>> = _dailyUsage.map { it[date] ?: emptyList() }

    override fun getWeeklyTotals(endDate: LocalDate): Flow<List<DailyTotal>> = _weeklyTotals

    override fun getWeeklyAppBreakdown(endDate: LocalDate): Flow<List<AppUsageSummary>> = _weeklyAppBreakdown

    override fun getBlockedAttempts(date: LocalDate): Flow<Int> = _blockedAttempts.map { it[date] ?: 0 }

    override suspend fun getTodayUsageMinutes(packageName: String): Int = _todayMinutes.value[packageName] ?: 0

    override suspend fun recordBlockedAttempt(packageName: String) {
        val today = LocalDate.now()
        _blockedAttempts.value = _blockedAttempts.value +
            (today to ((_blockedAttempts.value[today] ?: 0) + 1))
    }

    override suspend fun recordUsageSession(record: AppUsageRecord) {
        _recordedSessions.add(record)
    }
}
