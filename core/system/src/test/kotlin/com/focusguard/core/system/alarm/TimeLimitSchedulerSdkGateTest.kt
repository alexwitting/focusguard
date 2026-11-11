package com.focusguard.core.system.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Covers the inexact-alarm path, which needs Android 12+ with exact alarms denied, so it runs
 * under Robolectric. TimeLimitSchedulerTest covers the rest as a plain JVM test.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [31])
class TimeLimitSchedulerSdkGateTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Test
    fun `scheduleBlock uses the inexact alarm when exact-alarm scheduling isn't permitted`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = TimeLimitScheduler(context)

        scheduler.scheduleBlock("com.app", "App", remainingMinutes = 5)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }
}
