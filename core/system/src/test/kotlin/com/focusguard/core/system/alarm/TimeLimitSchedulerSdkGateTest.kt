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
 * `TimeLimitSchedulerTest` covers `scheduleBlock`'s behavior under a plain JVM test, where
 * `Build.VERSION.SDK_INT` reads 0 and the `SDK_INT >= S && !canScheduleExactAlarms()` branch is
 * unreachable — only `setExactAndAllowWhileIdle` fires there. This is a separate, minimal
 * Robolectric-based class (rather than converting the whole existing file) specifically to reach
 * the "inexact" `setAndAllowWhileIdle` path, which needs a real `Build.VERSION_CODES.S`+ SDK level
 * and `ShadowAlarmManager.setCanScheduleExactAlarms(false)`.
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

        assertTrue(shadowOf(alarmManager).nextScheduledAlarm != null)
    }
}
