package com.focusguard.core.system.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** `Build.VERSION.SDK_INT` is 0 in plain JVM tests, so only the exact-alarm path runs here. */
class TimeLimitSchedulerTest {
    private val context = mockk<Context>()
    private val alarmManager = mockk<AlarmManager>()
    private val pendingIntent = mockk<PendingIntent>()

    @BeforeEach
    fun setUp() {
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.packageName } returns "com.focusguard"

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(context, any(), any(), any()) } returns pendingIntent
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(PendingIntent::class)
    }

    @Test
    fun `scheduleBlock does nothing when remainingMinutes is zero`() {
        val scheduler = TimeLimitScheduler(context)

        scheduler.scheduleBlock("com.app", "App", remainingMinutes = 0)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 0) { alarmManager.cancel(any<PendingIntent>()) }
    }

    @Test
    fun `scheduleBlock does nothing when remainingMinutes is negative`() {
        val scheduler = TimeLimitScheduler(context)

        scheduler.scheduleBlock("com.app", "App", remainingMinutes = -5)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `scheduleBlock cancels any existing alarm then schedules an exact one`() {
        every { alarmManager.cancel(pendingIntent) } returns Unit
        every { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), pendingIntent) } returns Unit
        val scheduler = TimeLimitScheduler(context)

        scheduler.scheduleBlock("com.app", "App", remainingMinutes = 5)

        verify(exactly = 1) { alarmManager.cancel(pendingIntent) }
        verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), pendingIntent) }
    }

    @Test
    fun `scheduleBlock does not schedule when the pending intent cannot be built`() {
        every { PendingIntent.getBroadcast(context, any(), any(), any()) } returns null
        val scheduler = TimeLimitScheduler(context)

        scheduler.scheduleBlock("com.app", "App", remainingMinutes = 5)

        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `cancelBlock cancels the alarm when a pending intent already exists`() {
        every { alarmManager.cancel(pendingIntent) } returns Unit
        val scheduler = TimeLimitScheduler(context)

        scheduler.cancelBlock("com.app")

        verify(exactly = 1) { alarmManager.cancel(pendingIntent) }
    }

    @Test
    fun `cancelBlock does nothing when no pending intent exists for the package`() {
        every { PendingIntent.getBroadcast(context, any(), any(), any()) } returns null
        val scheduler = TimeLimitScheduler(context)

        scheduler.cancelBlock("com.app")

        verify(exactly = 0) { alarmManager.cancel(any<PendingIntent>()) }
    }

    @Test
    fun `scheduleBlock builds the pending intent with the blocked package and label extras`() {
        val labelSlot = slot<String>()
        every { anyConstructed<Intent>().putExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_LABEL, capture(labelSlot)) } returns mockk(relaxed = true)
        every { alarmManager.cancel(pendingIntent) } returns Unit
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } returns Unit
        val scheduler = TimeLimitScheduler(context)

        scheduler.scheduleBlock("com.app", "My App", remainingMinutes = 10)

        assertEquals("My App", labelSlot.captured)
    }
}
