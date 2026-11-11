package com.focusguard.core.data.repository

import com.focusguard.core.data.DataTestEnvironment
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.UsageSession
import com.focusguard.core.testing.FakeClockProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Usage written through the repository and read back through its reports, on a real Room
 * database, so the SQL (minute rounding, sub-minute filtering, top-app limits) runs too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageRepositoryImplTest {
    private val env = DataTestEnvironment()
    private val today = LocalDate.of(2026, 1, 15)
    private val clock = FakeClockProvider().apply { setNow(today.atTime(12, 0)) }
    private val rules = AppBlockRuleRepositoryImpl(env.db.appBlockRuleDao(), env.packageManager)
    private val repository = UsageRepositoryImpl(env.db.usageDao(), env.db.appBlockRuleDao(), env.packageManager, clock)

    @After
    fun tearDown() = env.close()

    /** Records [seconds] of use of [pkg] on [date], split into [parts] equal sessions. */
    private suspend fun use(
        pkg: String,
        seconds: Long,
        date: LocalDate = today,
        parts: Int = 1,
    ) {
        val start = date.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        val each = seconds / parts
        val sessions =
            (0 until parts).map { i ->
                val from = start.plusSeconds(i * each * 2)
                UsageSession(startTime = from, endTime = from.plusSeconds(each))
            }
        repository.recordUsageSession(AppUsageRecord(packageName = pkg, date = date, sessions = sessions))
    }

    @Test
    fun `today's list shows apps used for a minute or more, with labels and time limits`() =
        runTest {
            env.installApp("com.instagram.android", "Instagram")
            rules.upsertAppBlockRule(AppBlockRule(packageName = "com.instagram.android", label = "Instagram", strategy = BlockingStrategy.TimeLimit(30)))
            use("com.instagram.android", seconds = 10 * 60)
            use("com.android.settings", seconds = 20)
            use("com.uninstalled.app", seconds = 60)
            use("com.android.chrome", seconds = 75, parts = 3)

            val summaries = repository.getDailyUsage(today).first().associateBy { it.packageName }

            assertEquals(setOf("com.instagram.android", "com.uninstalled.app", "com.android.chrome"), summaries.keys)
            assertEquals("Instagram", summaries.getValue("com.instagram.android").label)
            assertEquals(10, summaries.getValue("com.instagram.android").totalMinutes)
            assertEquals(30, summaries.getValue("com.instagram.android").limitMinutes)
            assertEquals("com.uninstalled.app", summaries.getValue("com.uninstalled.app").label)
            assertNull(summaries.getValue("com.uninstalled.app").limitMinutes)
            assertEquals(1, summaries.getValue("com.android.chrome").totalMinutes)
        }

    @Test
    fun `an app with a non-time-limit rule shows no limit`() =
        runTest {
            rules.upsertAppBlockRule(AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.AlwaysBlock))
            use("com.app", seconds = 120)

            assertNull(
                repository
                    .getDailyUsage(today)
                    .first()
                    .single()
                    .limitMinutes,
            )
        }

    @Test
    fun `the week's daily totals count every session, and top apps skip sub-minute ones`() =
        runTest {
            env.installApp("com.google.android.youtube", "YouTube")
            use("com.android.settings", seconds = 30)
            use("com.instagram.android", seconds = 30)
            use("com.google.android.youtube", seconds = 90 * 60, date = today.minusDays(2))
            use("com.instagram.android", seconds = 40 * 60, date = today.minusDays(1))
            use("com.android.vending", seconds = 5, date = today.minusDays(3))
            use("com.too.old", seconds = 600, date = today.minusDays(7))

            val totals = repository.getWeeklyTotals(today).first()
            assertEquals(today, totals.last().date)
            assertEquals(1, totals.last().totalMinutes)
            assertEquals(listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1), today), totals.map { it.date })

            val top = repository.getWeeklyAppBreakdown(today).first()
            assertEquals(listOf("com.google.android.youtube", "com.instagram.android"), top.map { it.packageName })
            assertEquals("YouTube", top.first().label)
            assertEquals(90, top.first().totalMinutes)
        }

    @Test
    fun `the weekly breakdown lists at most five apps`() =
        runTest {
            (1..7).forEach { use("com.app$it", seconds = it * 60L) }

            assertEquals(5, repository.getWeeklyAppBreakdown(today).first().size)
        }

    @Test
    fun `today's minutes for one app add up its sessions on the clock's date`() =
        runTest {
            use("com.app", seconds = 5 * 60)
            use("com.app", seconds = 4 * 60)
            use("com.app", seconds = 60 * 60, date = today.minusDays(1))

            assertEquals(9, repository.getTodayUsageMinutes("com.app"))
            assertEquals(0, repository.getTodayUsageMinutes("com.other"))
        }

    @Test
    fun `blocked attempts are counted per day`() =
        runTest {
            repeat(2) { repository.recordBlockedAttempt("com.app") }
            clock.setNow(today.plusDays(1).atTime(8, 0))
            repository.recordBlockedAttempt("com.app")

            assertEquals(2, repository.getBlockedAttempts(today).first())
            assertEquals(1, repository.getBlockedAttempts(today.plusDays(1)).first())
        }

    @Test
    fun `pruning drops usage, blocks and extensions before the cutoff and keeps the rest`() =
        runTest {
            val old = today.minusDays(40)
            val settings = SettingsRepositoryImpl(env.dataStore, env.db.usageDao())
            use("com.old", seconds = 600, date = old)
            use("com.new", seconds = 600, date = today.minusDays(5))
            clock.setNow(old.atTime(12, 0))
            repository.recordBlockedAttempt("com.old")
            settings.recordExtensionTaken("com.old", old)
            clock.setNow(today.atTime(12, 0))
            repository.recordBlockedAttempt("com.new")
            settings.recordExtensionTaken("com.new", today)

            repository.pruneBefore(today.minusDays(30))

            assertEquals(emptyList<Any>(), repository.getDailyUsage(old).first())
            assertEquals(listOf("com.new"), repository.getWeeklyAppBreakdown(today.minusDays(2)).first().map { it.packageName })
            assertEquals(0, repository.getBlockedAttempts(old).first())
            assertEquals(1, repository.getBlockedAttempts(today).first())
            assertEquals(false, settings.hasTakenExtensionToday("com.old", old).first())
            assertEquals(true, settings.hasTakenExtensionToday("com.new", today).first())
        }
}
