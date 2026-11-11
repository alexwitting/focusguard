package com.focusguard.core.domain.usecase

import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.usecase.settings.EnableWebContentBlockingUseCase
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.settings.GetWebContentBlockingEnabledUseCase
import com.focusguard.core.domain.usecase.settings.RequestExtensionUseCase
import com.focusguard.core.domain.usecase.usage.GetBlockedAttemptsUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyAppBreakdownUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyTrendUseCase
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeSystemHealthRepository
import com.focusguard.core.testing.FakeUsageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** The read-side use cases (usage reports, health) and the once-a-day extension. */
class UsageAndSettingsTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val clock = FakeClockProvider().apply { setNow(today.atTime(12, 0)) }
    private val usage = FakeUsageRepository()
    private val settings = FakeSettingsRepository()

    private fun summary(
        pkg: String,
        minutes: Int,
    ) = AppUsageSummary(packageName = pkg, label = pkg, totalMinutes = minutes, limitMinutes = null)

    @Test
    fun `reports default to the clock's today and its week`() =
        runTest {
            usage.setBlockedAttempts(today, 3)
            usage.setBlockedAttempts(today.minusDays(1), 9)
            usage.setWeeklyTotals(DailyTotal(today.minusDays(7), 50), DailyTotal(today.minusDays(6), 40), DailyTotal(today, 20))
            usage.setWeeklyAppBreakdown(summary("com.a", 60))

            assertEquals(3, GetBlockedAttemptsUseCase(usage, clock)().first())
            assertEquals(listOf(40, 20), GetWeeklyTrendUseCase(usage, clock)().first().map { it.totalMinutes })
            assertEquals(listOf("com.a"), GetWeeklyAppBreakdownUseCase(usage, clock)().first().map { it.packageName })
        }

    @Test
    fun `reports for another day read that day`() =
        runTest {
            val yesterday = today.minusDays(1)
            usage.setDailyUsage(yesterday, summary("com.a", 12))
            usage.setBlockedAttempts(yesterday, 9)
            usage.setWeeklyTotals(DailyTotal(today.minusDays(7), 50), DailyTotal(today, 20))

            assertEquals(listOf(12), GetDailyUsageUseCase(usage)(yesterday).first().map { it.totalMinutes })
            assertTrue(GetDailyUsageUseCase(usage)(today).first().isEmpty())
            assertEquals(9, GetBlockedAttemptsUseCase(usage, clock)(yesterday).first())
            assertEquals(listOf(50), GetWeeklyTrendUseCase(usage, clock)(yesterday).first().map { it.totalMinutes })
        }

    @Test
    fun `one extension per app per day, and only when extensions are allowed`() =
        runTest {
            val requestExtension = RequestExtensionUseCase(settings, clock)

            assertFalse(requestExtension("com.a"), "extensions are off by default")

            settings.setGracePeriodEnabled(true)
            assertTrue(requestExtension("com.a"))
            assertFalse(requestExtension("com.a"), "second extension the same day")
            assertTrue(requestExtension("com.b"), "each app has its own")

            clock.setNow(today.plusDays(1).atTime(9, 0))
            assertTrue(requestExtension("com.a"), "a new day allows another")
        }

    @Test
    fun `the browser scan starts off and stays on once enabled`() =
        runTest {
            val enabled = GetWebContentBlockingEnabledUseCase(settings)
            assertFalse(enabled().first())

            EnableWebContentBlockingUseCase(settings)()

            assertTrue(enabled().first())
        }

    @Test
    fun `health reads come straight from the system`() =
        runTest {
            val system = FakeSystemHealthRepository()
            val health = GetSystemHealthUseCase(system)
            val running = SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false)
            val granted = PermissionsHealth(true, true, true, true, true)

            system.setSystemHealth(running)
            system.setPermissionsHealth(granted)

            assertEquals(running, health.observeSystemHealth().first())
            assertEquals(running, health.checkSystemHealthNow())
            assertEquals(granted, health.observePermissionsHealth().first())
            assertEquals(granted, health.checkPermissionsNow())
        }
}
