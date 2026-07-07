package com.focusguard.feature.home

import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.usage.GetBlockedAttemptsUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyAppBreakdownUseCase
import com.focusguard.core.domain.usecase.usage.GetWeeklyTrendUseCase
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeSystemHealthRepository
import com.focusguard.core.testing.FakeUsageRepository
import com.focusguard.core.testing.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val usageRepository = FakeUsageRepository()
    private val clockProvider = FakeClockProvider()
    private val systemHealthRepository = FakeSystemHealthRepository()

    @BeforeEach
    fun setUp() {
        // Log.e isn't mocked under a plain JVM unit test (no Robolectric here) — only needed by
        // the "failing upstream flow" test below, but harmless to always stub.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            getDailyUsage = GetDailyUsageUseCase(usageRepository),
            getWeeklyTrend = GetWeeklyTrendUseCase(usageRepository, clockProvider),
            getWeeklyAppBreakdown = GetWeeklyAppBreakdownUseCase(usageRepository, clockProvider),
            getBlockedAttempts = GetBlockedAttemptsUseCase(usageRepository, clockProvider),
            getSystemHealth = GetSystemHealthUseCase(systemHealthRepository),
            clockProvider = clockProvider,
        )

    @Test
    fun `combines daily usage, weekly totals, and blocked attempts into state`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            usageRepository.setDailyUsage(
                today,
                AppUsageSummary(packageName = "com.app", label = "App", totalMinutes = 42, limitMinutes = 60),
            )
            usageRepository.setWeeklyTotals(DailyTotal(today, 42))
            usageRepository.setBlockedAttempts(today, 3)

            val viewModel = createViewModel()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(42, viewModel.state.value.totalMinutesToday)
            assertEquals(1, viewModel.state.value.weeklyTotals.size)
            assertEquals(3, viewModel.state.value.blockedAttempts)
        }

    @Test
    fun `significantDailyUsage filters out apps used a minute or less`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            usageRepository.setDailyUsage(
                today,
                AppUsageSummary(packageName = "com.tiny", label = "Tiny", totalMinutes = 1, limitMinutes = null),
                AppUsageSummary(packageName = "com.real", label = "Real", totalMinutes = 15, limitMinutes = null),
            )

            val viewModel = createViewModel()

            assertEquals(
                listOf("com.real"),
                viewModel.state.value.significantDailyUsage
                    .map { it.packageName },
            )
        }

    @Test
    fun `weeklyAppBreakdown reflects the repository's per-app weekly totals`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            usageRepository.setWeeklyAppBreakdown(
                AppUsageSummary(packageName = "com.app", label = "App", totalMinutes = 300, limitMinutes = null),
            )

            val viewModel = createViewModel()

            assertEquals(
                listOf("com.app"),
                viewModel.state.value.weeklyAppBreakdown
                    .map { it.packageName },
            )
        }

    @Test
    fun `systemHealth reflects the repository's current reading`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            systemHealthRepository.setSystemHealth(
                SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false),
            )

            val viewModel = createViewModel()

            assertFalse(viewModel.state.value.systemHealth.isFullyOperational)
            assertTrue(viewModel.state.value.systemHealth.accessibilityServiceActive)
        }

    @Test
    fun `a failing upstream flow surfaces a load error instead of spinning forever`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            val failingRepository =
                mockk<UsageRepository> {
                    every { getDailyUsage(today) } returns flow { throw IllegalStateException("boom") }
                }

            val viewModel =
                HomeViewModel(
                    getDailyUsage = GetDailyUsageUseCase(failingRepository),
                    getWeeklyTrend = GetWeeklyTrendUseCase(usageRepository, clockProvider),
                    getWeeklyAppBreakdown = GetWeeklyAppBreakdownUseCase(usageRepository, clockProvider),
                    getBlockedAttempts = GetBlockedAttemptsUseCase(usageRepository, clockProvider),
                    getSystemHealth = GetSystemHealthUseCase(systemHealthRepository),
                    clockProvider = clockProvider,
                )

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.loadError)
        }
}
