package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface UsageRepository {
    fun getDailyUsage(date: LocalDate): Flow<List<AppUsageSummary>>

    fun getWeeklyTotals(endDate: LocalDate): Flow<List<DailyTotal>>

    fun getWeeklyAppBreakdown(endDate: LocalDate): Flow<List<AppUsageSummary>>

    fun getBlockedAttempts(date: LocalDate): Flow<Int>

    suspend fun getTodayUsageMinutes(packageName: String): Int

    suspend fun recordBlockedAttempt(packageName: String)

    suspend fun recordUsageSession(record: AppUsageRecord)

    /** Deletes usage sessions, blocked attempts and extension records dated before [date]. */
    suspend fun pruneBefore(date: LocalDate)
}
