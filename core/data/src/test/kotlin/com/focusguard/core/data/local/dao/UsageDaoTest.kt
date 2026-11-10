package com.focusguard.core.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusguard.core.data.local.FocusGuardDatabase
import com.focusguard.core.data.local.entity.AppUsageSessionEntity
import com.focusguard.core.data.local.entity.BlockedAttemptEntity
import com.focusguard.core.data.local.entity.ExtensionRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs UsageDao's real SQL against an in-memory Room database. JUnit4 + Robolectric's own
 * runner (see :core:system's FocusGuardAccessibilityServiceTest for why not the JUnit5 bridge).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageDaoTest {
    private lateinit var db: FocusGuardDatabase
    private lateinit var dao: UsageDao

    private val today = 20_000L

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusGuardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.usageDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(
        packageName: String,
        seconds: Long,
        date: Long = today,
    ) {
        dao.insertSession(
            AppUsageSessionEntity(packageName = packageName, date = date, startTime = 0, endTime = seconds * 1_000),
        )
    }

    @Test
    fun `daily usage leaves out apps used for under a minute`() =
        runTest {
            session("com.android.settings", seconds = 20)
            session("com.instagram.android", seconds = 10 * 60)
            session("com.whatsapp", seconds = 60)

            val rows = dao.getDailyUsage(today).first()

            assertEquals(listOf("com.instagram.android" to 10L, "com.whatsapp" to 1L), rows.map { it.packageName to it.totalMinutes })
        }

    @Test
    fun `several short sessions that add up to a minute still count`() =
        runTest {
            repeat(3) { session("com.android.chrome", seconds = 25) }

            val rows = dao.getDailyUsage(today).first()

            assertEquals(listOf("com.android.chrome" to 1L), rows.map { it.packageName to it.totalMinutes })
        }

    @Test
    fun `weekly top apps skip sub-minute apps instead of filling the limit with them`() =
        runTest {
            session("com.android.settings", seconds = 30)
            session("com.google.android.youtube", seconds = 90 * 60, date = today - 2)
            session("com.instagram.android", seconds = 40 * 60, date = today - 1)
            session("com.android.vending", seconds = 5, date = today - 3)

            val rows = dao.getWeeklyAppTotals(today - 6, today, limit = 5).first()

            assertEquals(
                listOf("com.google.android.youtube", "com.instagram.android"),
                rows.map { it.packageName },
            )
        }

    @Test
    fun `daily totals still include sub-minute sessions`() =
        runTest {
            session("com.android.settings", seconds = 30)
            session("com.instagram.android", seconds = 30)

            val totals = dao.getWeeklyTotals(today - 6, today).first()

            assertEquals(listOf(today to 1L), totals.map { it.date to it.totalMinutes })
        }

    @Test
    fun `pruneBefore deletes sessions, blocks and extensions older than the cutoff and keeps the rest`() =
        runTest {
            session("com.old", seconds = 600, date = today - 40)
            session("com.new", seconds = 600, date = today - 5)
            dao.insertBlockedAttempt(BlockedAttemptEntity(packageName = "com.old", date = today - 40, timestamp = 0))
            dao.insertBlockedAttempt(BlockedAttemptEntity(packageName = "com.new", date = today, timestamp = 0))
            dao.recordExtension(ExtensionRecordEntity("com.old", today - 40))
            dao.recordExtension(ExtensionRecordEntity("com.new", today))

            dao.pruneBefore(today - 30)

            assertEquals(listOf("com.new"), dao.getWeeklyAppTotals(today - 60, today, limit = 10).first().map { it.packageName })
            assertEquals(0, dao.getBlockedAttempts(today - 40).first())
            assertEquals(1, dao.getBlockedAttempts(today).first())
            assertEquals(0, dao.countExtension("com.old", today - 40).first())
            assertEquals(1, dao.countExtension("com.new", today).first())
        }
}
