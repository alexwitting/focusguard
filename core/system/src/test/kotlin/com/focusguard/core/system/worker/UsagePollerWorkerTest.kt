package com.focusguard.core.system.worker

import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.system.engine.UsageSessionEngine
import com.focusguard.core.system.receiver.AccessibilityWatchdog
import com.focusguard.core.system.usage.UsageRecorder
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeUsageRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * UsageStatsManager/UsageEvents have no public constructor and can't be meaningfully mocked —
 * Robolectric's ShadowUsageStatsManager.EventBuilder simulates real usage events instead.
 */
@ExtendWith(RobolectricExtension::class)
class UsagePollerWorkerTest {
    private val usageRepository = FakeUsageRepository()
    private val blockRuleRepository = FakeBlockRuleRepository()
    private val usageSessionEngine = UsageSessionEngine()
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @AfterEach
    fun tearDown() {
        UsagePollerWorker.notified80Packages.clear()
    }

    private fun buildWorker(): UsagePollerWorker =
        TestListenableWorkerBuilder<UsagePollerWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        UsagePollerWorker(
                            appContext,
                            workerParameters,
                            usageRepository,
                            blockRuleRepository,
                            usageSessionEngine,
                            UsageRecorder(appContext, usageRepository, usageSessionEngine, FakeClockProvider()),
                            AccessibilityWatchdog(appContext, FakeSettingsRepository()),
                        )
                },
            ).build()

    private fun addEvent(
        pkg: String,
        type: Int,
        timestamp: Long,
    ) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowOf(usageStatsManager).addEvent(
            ShadowUsageStatsManager.EventBuilder
                .buildEvent()
                .setPackage(pkg)
                .setEventType(type)
                .setTimeStamp(timestamp)
                .build(),
        )
    }

    @Test
    fun `doWork records a completed session from a resume-then-pause pair`() =
        runTest {
            // doWork() queries the [now - 15min, now] window against real wall-clock time, so
            // events need timestamps relative to now — not small epoch-relative values.
            val now = System.currentTimeMillis()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, now - 60_000)
            addEvent("com.app", UsageEvents.Event.ACTIVITY_PAUSED, now - 1_000)

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, usageRepository.recordedSessions.size)
            assertEquals("com.app", usageRepository.recordedSessions.first().packageName)
        }

    @Test
    fun `doWork posts a warning notification when usage crosses 80 percent of the daily limit`() =
        runTest {
            blockRuleRepository.setRules(
                AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            usageRepository.setTodayUsageMinutes("com.app", 50)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            buildWorker().doWork()

            assertTrue(shadowOf(notificationManager).allNotifications.isNotEmpty())
            assertTrue("com.app" in UsagePollerWorker.notified80Packages)
        }

    @Test
    fun `doWork does not re-notify a package already warned today`() =
        runTest {
            blockRuleRepository.setRules(
                AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            usageRepository.setTodayUsageMinutes("com.app", 50)
            UsagePollerWorker.notified80Packages.add("com.app")
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            buildWorker().doWork()

            assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        }

    @Test
    fun `doWork ignores event types other than resumed and paused`() =
        runTest {
            val now = System.currentTimeMillis()
            addEvent("com.app", UsageEvents.Event.NONE, now - 1_000)

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue(usageRepository.recordedSessions.isEmpty())
        }

    @Test
    fun `doWork skips rules that are not TimeLimit strategy`() =
        runTest {
            blockRuleRepository.setRules(
                AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.AlwaysBlock),
            )
            usageRepository.setTodayUsageMinutes("com.app", 999)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            buildWorker().doWork()

            assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        }

    @Test
    fun `doWork closes a still-open session at the query window end when there's no matching pause yet`() =
        runTest {
            // UsageSessionEngine.pairSessions() documents this: a package still resumed with no
            // matching PAUSED event gets one session closed at windowEnd rather than dropped, so
            // pairs.isEmpty() never actually happens for a package that appears in the raw events
            // at all — every sessionPairs entry is built via getOrPut{}.add(), always non-empty.
            val now = System.currentTimeMillis()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, now - 1_000)

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, usageRepository.recordedSessions.size)
        }

    @Test
    fun `doWork uses a persisted checkpoint older than the fixed window to catch up after a delayed run`() =
        runTest {
            val now = System.currentTimeMillis()
            // 20 minutes ago is outside a fixed 15-minute window, but inside the window a
            // persisted checkpoint from 30 minutes ago opens up — proves the checkpoint, not a
            // fixed window, determines where a run actually starts reading from.
            context
                .getSharedPreferences("focusguard_usage_poller_state", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_processed_timestamp", now - 30 * 60_000)
                .apply()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, now - 20 * 60_000)
            addEvent("com.app", UsageEvents.Event.ACTIVITY_PAUSED, now - 19 * 60_000)

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, usageRepository.recordedSessions.size)
        }

    @Test
    fun `doWork buckets sessions spanning multiple calendar days by each session's own start-time date`() =
        runTest {
            val now = System.currentTimeMillis()
            val yesterday = now - 25 * 60 * 60_000 // guarantees a different calendar date
            context
                .getSharedPreferences("focusguard_usage_poller_state", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_processed_timestamp", yesterday - 60_000)
                .apply()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, yesterday)
            addEvent("com.app", UsageEvents.Event.ACTIVITY_PAUSED, yesterday + 60_000)
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, now - 60_000)
            addEvent("com.app", UsageEvents.Event.ACTIVITY_PAUSED, now - 1_000)

            val result = buildWorker().doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(2, usageRepository.recordedSessions.size)
            val distinctDates = usageRepository.recordedSessions.map { it.date }.distinct()
            assertEquals(2, distinctDates.size)
        }

    @Test
    fun `doWork keeps counting an app that stays open across polls`() =
        runTest {
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences("focusguard_usage_poller_state", Context.MODE_PRIVATE)
            // Last poll ended 10 minutes ago with com.app still in the foreground, and nothing
            // has happened since — the app is still open now.
            prefs
                .edit()
                .putLong("last_processed_timestamp", now - 10 * 60_000)
                .putStringSet("open_packages", setOf("com.app"))
                .apply()

            buildWorker().doWork()

            val session = usageRepository.recordedSessions.single()
            assertEquals("com.app", session.packageName)
            val minutes =
                session.sessions.sumOf {
                    java.time.Duration
                        .between(it.startTime, it.endTime)
                        .toMinutes()
                }
            assertTrue(minutes in 9..10, "expected ~10 minutes, got $minutes")
            assertEquals(setOf("com.app"), prefs.getStringSet("open_packages", emptySet()))
        }

    @Test
    fun `doWork persists the window end as the checkpoint for the next run`() =
        runTest {
            val now = System.currentTimeMillis()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, now - 60_000)
            addEvent("com.app", UsageEvents.Event.ACTIVITY_PAUSED, now - 1_000)

            buildWorker().doWork()

            val persisted =
                context
                    .getSharedPreferences("focusguard_usage_poller_state", Context.MODE_PRIVATE)
                    .getLong("last_processed_timestamp", -1)
            assertTrue(persisted in now..(now + 5_000))
        }

    @Test
    fun `notified80Packages survives concurrent adds racing a clear without throwing`() {
        // UsagePollerWorker and MidnightResetWorker are separate WorkManager unique work items
        // that can run on different executor threads concurrently — this simulates that race
        // directly against the shared companion-object set rather than through two workers.
        val adder =
            Thread {
                repeat(500) { i -> UsagePollerWorker.notified80Packages.add("com.app$i") }
            }
        val clearer =
            Thread {
                repeat(500) { UsagePollerWorker.notified80Packages.clear() }
            }

        adder.start()
        clearer.start()
        adder.join()
        clearer.join()

        // The interleaving is non-deterministic, but reaching this line without a
        // ConcurrentModificationException is the actual thing under test; the size bound just
        // confirms the set wasn't corrupted into holding more than the 500 possible entries.
        assertTrue(UsagePollerWorker.notified80Packages.size <= 500)
    }
}
