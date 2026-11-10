package com.focusguard.core.data.repository

import android.content.pm.PackageManager
import com.focusguard.core.data.local.dao.AppBlockRuleDao
import com.focusguard.core.data.local.dao.UsageDao
import com.focusguard.core.data.local.entity.AppUsageSessionEntity
import com.focusguard.core.data.local.entity.BlockedAttemptEntity
import com.focusguard.core.data.local.entity.toDomain
import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class UsageRepositoryImpl
    @Inject
    constructor(
        private val usageDao: UsageDao,
        private val blockRuleDao: AppBlockRuleDao,
        private val packageManager: PackageManager,
        private val clockProvider: ClockProvider,
    ) : UsageRepository {
        override fun getDailyUsage(date: LocalDate): Flow<List<AppUsageSummary>> =
            combine(
                usageDao.getDailyUsage(date.toEpochDay()),
                blockRuleDao.getAll(),
            ) { usageRows, ruleEntities ->
                val rulesByPackage = ruleEntities.associate { it.packageName to it.toDomain() }
                usageRows.map { row ->
                    val limitMinutes =
                        (rulesByPackage[row.packageName]?.strategy as? BlockingStrategy.TimeLimit)
                            ?.dailyLimitMinutes
                    AppUsageSummary(
                        packageName = row.packageName,
                        label = getAppLabel(row.packageName),
                        totalMinutes = row.totalMinutes.toInt(),
                        limitMinutes = limitMinutes,
                    )
                }
            }.flowOn(Dispatchers.IO)

        override fun getWeeklyTotals(endDate: LocalDate): Flow<List<DailyTotal>> {
            val startDate = endDate.minusDays(6)
            return usageDao
                .getWeeklyTotals(startDate.toEpochDay(), endDate.toEpochDay())
                .map { rows ->
                    rows.map { row ->
                        DailyTotal(
                            date = LocalDate.ofEpochDay(row.date),
                            totalMinutes = row.totalMinutes.toInt(),
                        )
                    }
                }
        }

        override fun getWeeklyAppBreakdown(endDate: LocalDate): Flow<List<AppUsageSummary>> {
            val startDate = endDate.minusDays(6)
            return usageDao
                .getWeeklyAppTotals(startDate.toEpochDay(), endDate.toEpochDay(), TOP_APPS_LIMIT)
                .map { rows ->
                    rows.map { row ->
                        AppUsageSummary(
                            packageName = row.packageName,
                            label = getAppLabel(row.packageName),
                            totalMinutes = row.totalMinutes.toInt(),
                            limitMinutes = null,
                        )
                    }
                }.flowOn(Dispatchers.IO)
        }

        override fun getBlockedAttempts(date: LocalDate): Flow<Int> = usageDao.getBlockedAttempts(date.toEpochDay())

        override suspend fun getTodayUsageMinutes(packageName: String): Int {
            val today = clockProvider.today().toEpochDay()
            return usageDao.getTodayUsageMinutes(packageName, today).toInt()
        }

        override suspend fun recordBlockedAttempt(packageName: String) {
            usageDao.insertBlockedAttempt(
                BlockedAttemptEntity(
                    packageName = packageName,
                    date = clockProvider.today().toEpochDay(),
                    timestamp = clockProvider.epochMillis(),
                ),
            )
        }

        override suspend fun recordUsageSession(record: AppUsageRecord) {
            record.sessions.forEach { session ->
                usageDao.insertSession(
                    AppUsageSessionEntity(
                        packageName = record.packageName,
                        date = record.date.toEpochDay(),
                        startTime = session.startTime.toEpochMilli(),
                        endTime = session.endTime.toEpochMilli(),
                    ),
                )
            }
        }

        override suspend fun pruneBefore(date: LocalDate) = usageDao.pruneBefore(date.toEpochDay())

        private fun getAppLabel(packageName: String): String =
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }

        private companion object {
            const val TOP_APPS_LIMIT = 5
        }
    }
