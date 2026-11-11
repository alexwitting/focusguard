package com.focusguard.core.system.alarm

import android.content.Context
import android.content.Intent
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.system.service.FocusGuardAccessibilityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Android SDK classes throw in plain JVM tests, so the intents involved are mocked. */
class TimeLimitAlarmReceiverTest {
    private val context = mockk<Context>()
    private val receiver = TimeLimitAlarmReceiver()

    @BeforeEach
    fun setUp() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setClassName(any<String>(), any()) } returns mockk()
        every { anyConstructed<Intent>().addFlags(any()) } returns mockk()
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(Intent::class)
        setServiceInstance(null)
    }

    /**
     * Sets [FocusGuardAccessibilityService.instance] through reflection. Its backing field is a
     * static on the outer class, not on Companion.
     */
    private fun setServiceInstance(service: FocusGuardAccessibilityService?) {
        val field = FocusGuardAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, service)
    }

    private fun intent(
        packageName: String? = null,
        label: String? = null,
        reason: String? = null,
    ) = mockk<Intent> {
        every { getStringExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_PACKAGE) } returns packageName
        every { getStringExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_LABEL) } returns label
        every { getStringExtra(TimeLimitAlarmReceiver.EXTRA_BLOCK_REASON) } returns reason
    }

    @Test
    fun `passes the scheduled block reason through, such as bedtime starting`() {
        val service = mockk<FocusGuardAccessibilityService>(relaxed = true)
        setServiceInstance(service)

        receiver.onReceive(context, intent(packageName = "com.app", label = "App", reason = BlockReason.BEDTIME.name))

        verify(exactly = 1) { service.launchBlockedScreen("com.app", "App", BlockReason.BEDTIME) }
    }

    @Test
    fun `ignores intents missing the blocked-package extra`() {
        receiver.onReceive(context, intent(packageName = null))

        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `delegates to the running accessibility service when one is available`() {
        val service = mockk<FocusGuardAccessibilityService>(relaxed = true)
        setServiceInstance(service)

        receiver.onReceive(context, intent(packageName = "com.app", label = "App"))

        verify(exactly = 1) { service.launchBlockedScreen("com.app", "App", BlockReason.TIME_LIMIT_REACHED) }
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `falls back to starting the blocked activity directly when no service is running`() {
        every { context.packageName } returns "com.focusguard"
        every { context.startActivity(any()) } returns Unit

        receiver.onReceive(context, intent(packageName = "com.app", label = "App"))

        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun `falls back to the package name when the label extra is missing`() {
        every { context.packageName } returns "com.focusguard"
        every { context.startActivity(any()) } returns Unit

        receiver.onReceive(context, intent(packageName = "com.app", label = null))

        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun `an unknown reason falls back to the time-limit block`() {
        val service = mockk<FocusGuardAccessibilityService>(relaxed = true)
        setServiceInstance(service)

        receiver.onReceive(context, intent(packageName = "com.app", label = "App", reason = "NOT_A_REASON"))

        verify(exactly = 1) { service.launchBlockedScreen("com.app", "App", BlockReason.TIME_LIMIT_REACHED) }
    }
}
