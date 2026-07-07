package com.focusguard.core.system.receiver

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Every real Android SDK method throws under a plain JVM unit test (no
 * `unitTests.isReturnDefaultValues`, matching PinRepositoryImplTest's precedent) — so both the
 * received [Intent] and the one `postAlert` builds internally must be mocked, never constructed
 * for real.
 */
class WatchdogReceiverTest {
    private val context = mockk<Context>()
    private val accessibilityManager = mockk<AccessibilityManager>()
    private val notificationManager = mockk<NotificationManager>()
    private val receiver = WatchdogReceiver()

    private companion object {
        const val PACKAGE_NAME = "com.focusguard"
        const val NOTIFICATION_ID = 2001
    }

    @BeforeEach
    fun setUp() {
        every { context.packageName } returns PACKAGE_NAME
        every { context.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns accessibilityManager
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()

        // Covers the Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = ... }
        // postAlert() builds internally.
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setFlags(any()) } returns mockk()

        // NotificationCompat.Builder.build() delegates to a real android.app.Notification.Builder
        // internally (setWhen, etc.) — un-mockable framework calls unrelated to what postAlert()
        // actually decides, so the whole fluent chain is mocked instead.
        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentTitle(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentText(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setPriority(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentIntent(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setOngoing(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(PendingIntent::class)
        unmockkConstructor(Intent::class)
        unmockkConstructor(NotificationCompat.Builder::class)
    }

    private fun enabledAccessibilityService(): AccessibilityServiceInfo =
        mockk<AccessibilityServiceInfo> {
            every { resolveInfo } returns
                ResolveInfo().apply {
                    serviceInfo = ServiceInfo().apply { packageName = PACKAGE_NAME }
                }
        }

    @Test
    fun `ignores intents that are not ACTION_SCREEN_ON`() {
        val intent = mockk<Intent> { every { action } returns Intent.ACTION_SCREEN_OFF }

        receiver.onReceive(context, intent)

        verify(exactly = 0) { context.getSystemService(any<String>()) }
    }

    @Test
    fun `cancels the alert notification when the accessibility service is enabled`() {
        val intent = mockk<Intent> { every { action } returns Intent.ACTION_SCREEN_ON }
        every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
            listOf(enabledAccessibilityService())
        every { notificationManager.cancel(NOTIFICATION_ID) } returns Unit

        receiver.onReceive(context, intent)

        verify(exactly = 1) { notificationManager.cancel(NOTIFICATION_ID) }
        verify(exactly = 0) { notificationManager.notify(any(), any()) }
    }

    @Test
    fun `posts an alert notification when the accessibility service is disabled`() {
        val intent = mockk<Intent> { every { action } returns Intent.ACTION_SCREEN_ON }
        every { accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) } returns
            emptyList()
        every { notificationManager.createNotificationChannel(any()) } returns Unit
        every { notificationManager.notify(NOTIFICATION_ID, any()) } returns Unit

        receiver.onReceive(context, intent)

        verify(exactly = 1) { notificationManager.createNotificationChannel(any()) }
        verify(exactly = 1) { notificationManager.notify(NOTIFICATION_ID, any()) }
        verify(exactly = 0) { notificationManager.cancel(any<Int>()) }
    }
}
