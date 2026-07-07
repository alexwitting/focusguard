package com.focusguard.core.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focusguard.core.data.local.dao.AppBlockRuleDao
import com.focusguard.core.data.local.dao.DailyUsageSummaryRow
import com.focusguard.core.data.local.dao.UsageDao
import com.focusguard.core.data.local.dao.WeeklyTotalRow
import com.focusguard.core.data.local.entity.BlockedAttemptEntity
import com.focusguard.core.data.local.entity.toEntity
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.UsageSession
import com.focusguard.core.testing.FakeClockProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class UsageRepositoryImplTest {
    private val usageDao = mockk<UsageDao>()
    private val blockRuleDao = mockk<AppBlockRuleDao>()
    private val packageManager = mockk<PackageManager>()
    private val clockProvider = FakeClockProvider()
    private val repository = UsageRepositoryImpl(usageDao, blockRuleDao, packageManager, clockProvider)

    @Test
    fun `getDailyUsage merges usage rows with the app's time-limit rule and resolves labels`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            val rule =
                AppBlockRule(
                    packageName = "com.app",
                    label = "App",
                    strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60),
                )
            every { usageDao.getDailyUsage(date.toEpochDay()) } returns
                flowOf(listOf(DailyUsageSummaryRow(packageName = "com.app", totalMinutes = 42)))
            every { blockRuleDao.getAll() } returns flowOf(listOf(rule.toEntity()))
            val appInfo = ApplicationInfo().apply { packageName = "com.app" }
            every { packageManager.getApplicationInfo("com.app", 0) } returns appInfo
            every { packageManager.getApplicationLabel(appInfo) } returns "App"

            val result = repository.getDailyUsage(date).first()

            assertEquals(1, result.size)
            assertEquals("com.app", result.first().packageName)
            assertEquals("App", result.first().label)
            assertEquals(42, result.first().totalMinutes)
            assertEquals(60, result.first().limitMinutes)
        }

    @Test
    fun `getDailyUsage falls back to the package name when the label cannot be resolved`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            every { usageDao.getDailyUsage(date.toEpochDay()) } returns
                flowOf(listOf(DailyUsageSummaryRow(packageName = "com.unknown", totalMinutes = 5)))
            every { blockRuleDao.getAll() } returns flowOf(emptyList())
            every { packageManager.getApplicationInfo("com.unknown", 0) } throws
                PackageManager.NameNotFoundException()

            val result = repository.getDailyUsage(date).first()

            assertEquals("com.unknown", result.first().label)
            assertEquals(null, result.first().limitMinutes)
        }

    @Test
    fun `getWeeklyTotals maps rows to domain DailyTotal`() =
        runTest {
            val endDate = LocalDate.of(2026, 1, 15)
            every { usageDao.getWeeklyTotals(endDate.minusDays(6).toEpochDay(), endDate.toEpochDay()) } returns
                flowOf(listOf(WeeklyTotalRow(date = endDate.toEpochDay(), totalMinutes = 90)))

            val result = repository.getWeeklyTotals(endDate).first()

            assertEquals(listOf(endDate), result.map { it.date })
            assertEquals(listOf(90), result.map { it.totalMinutes })
        }

    @Test
    fun `getWeeklyAppBreakdown maps rows to domain AppUsageSummary and resolves labels`() =
        runTest {
            val endDate = LocalDate.of(2026, 1, 15)
            every {
                usageDao.getWeeklyAppTotals(endDate.minusDays(6).toEpochDay(), endDate.toEpochDay(), 5)
            } returns flowOf(listOf(DailyUsageSummaryRow(packageName = "com.app", totalMinutes = 120)))
            val appInfo = ApplicationInfo().apply { packageName = "com.app" }
            every { packageManager.getApplicationInfo("com.app", 0) } returns appInfo
            every { packageManager.getApplicationLabel(appInfo) } returns "App"

            val result = repository.getWeeklyAppBreakdown(endDate).first()

            assertEquals(1, result.size)
            assertEquals("com.app", result.first().packageName)
            assertEquals("App", result.first().label)
            assertEquals(120, result.first().totalMinutes)
            assertEquals(null, result.first().limitMinutes)
        }

    @Test
    fun `getBlockedAttempts delegates to the DAO`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            every { usageDao.getBlockedAttempts(date.toEpochDay()) } returns flowOf(4)

            assertEquals(4, repository.getBlockedAttempts(date).first())
        }

    @Test
    fun `getTodayUsageMinutes delegates to the DAO for today's epoch day`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            coEvery { usageDao.getTodayUsageMinutes("com.app", today.toEpochDay()) } returns 33L

            assertEquals(33, repository.getTodayUsageMinutes("com.app"))
        }

    @Test
    fun `recordBlockedAttempt inserts an entity for today`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            val slot = slot<BlockedAttemptEntity>()
            coEvery { usageDao.insertBlockedAttempt(capture(slot)) } returns Unit

            repository.recordBlockedAttempt("com.app")

            assertEquals("com.app", slot.captured.packageName)
            assertEquals(today.toEpochDay(), slot.captured.date)
        }

    @Test
    fun `recordUsageSession inserts one entity per session`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            val record =
                AppUsageRecord(
                    packageName = "com.app",
                    date = date,
                    totalMinutes = 20,
                    sessions =
                        listOf(
                            UsageSession(startTime = Instant.ofEpochMilli(1_000), endTime = Instant.ofEpochMilli(61_000)),
                        ),
                )
            coEvery { usageDao.insertSession(any()) } returns Unit

            repository.recordUsageSession(record)

            coVerify(exactly = 1) {
                usageDao.insertSession(
                    match { it.packageName == "com.app" && it.date == date.toEpochDay() && it.startTime == 1_000L && it.endTime == 61_000L },
                )
            }
        }
}
