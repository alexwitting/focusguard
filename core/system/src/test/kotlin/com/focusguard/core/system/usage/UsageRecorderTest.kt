package com.focusguard.core.system.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusguard.core.system.engine.UsageSessionEngine
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeUsageRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

@ExtendWith(RobolectricExtension::class)
class UsageRecorderTest {
    private lateinit var context: Context
    private val usageRepository = FakeUsageRepository()
    private val clock = FakeClockProvider()

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun recorder() = UsageRecorder(context, usageRepository, UsageSessionEngine(), clock)

    private fun prefs() = context.getSharedPreferences("focusguard_usage_poller_state", Context.MODE_PRIVATE)

    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun addEvent(
        pkg: String,
        type: Int,
        at: Long,
    ) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowOf(usm).addEvent(
            ShadowUsageStatsManager.EventBuilder
                .buildEvent()
                .setPackage(pkg)
                .setEventType(type)
                .setTimeStamp(at)
                .build(),
        )
    }

    @Test
    fun `an app still marked open after a long gap is not counted for the whole gap`() =
        runTest {
            val now = LocalDateTime.of(2026, 3, 10, 12, 0)
            clock.setNow(now)
            prefs()
                .edit()
                .putLong("last_processed_timestamp", millis(now.minusHours(8)))
                .putStringSet("open_packages", setOf("com.app"))
                .apply()

            recorder().recordUpToNow()

            assertTrue(usageRepository.recordedSessions.isEmpty())
        }

    @Test
    fun `a checkpoint at or after now records nothing`() =
        runTest {
            val now = LocalDateTime.of(2026, 3, 10, 12, 0)
            clock.setNow(now)
            prefs().edit().putLong("last_processed_timestamp", millis(now)).apply()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, millis(now.minusMinutes(5)))

            recorder().recordUpToNow()

            assertTrue(usageRepository.recordedSessions.isEmpty())
            assertEquals(millis(now), prefs().getLong("last_processed_timestamp", -1))
        }

    @Test
    fun `a late-night session is split at midnight between the two days`() =
        runTest {
            val now = LocalDateTime.of(2026, 3, 11, 0, 30)
            clock.setNow(now)
            prefs().edit().putLong("last_processed_timestamp", millis(now.minusHours(1))).apply()
            addEvent("com.app", UsageEvents.Event.ACTIVITY_RESUMED, millis(LocalDateTime.of(2026, 3, 10, 23, 50)))
            addEvent("com.app", UsageEvents.Event.ACTIVITY_PAUSED, millis(LocalDateTime.of(2026, 3, 11, 0, 20)))

            recorder().recordUpToNow()

            val byDate =
                usageRepository.recordedSessions.associate { record ->
                    record.date to record.sessions.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                }
            assertEquals(mapOf(now.toLocalDate().minusDays(1) to 10L, now.toLocalDate() to 20L), byDate)
        }

    @Test
    fun `an app open at the last poll keeps counting until the screen turns off, ignoring other events`() =
        runTest {
            val now = LocalDateTime.of(2026, 3, 10, 12, 0)
            clock.setNow(now)
            prefs()
                .edit()
                .putLong("last_processed_timestamp", millis(now.minusMinutes(15)))
                .putStringSet("open_packages", setOf("com.app"))
                .apply()
            addEvent("com.app", UsageEvents.Event.USER_INTERACTION, millis(now.minusMinutes(10)))
            addEvent("android", UsageEvents.Event.SCREEN_NON_INTERACTIVE, millis(now.minusMinutes(5)))

            recorder().recordUpToNow()

            val minutes =
                usageRepository.recordedSessions
                    .single()
                    .sessions
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            assertEquals(10L, minutes)
            assertTrue(prefs().getStringSet("open_packages", null).orEmpty().isEmpty())
        }
}
