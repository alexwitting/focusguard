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
        // android.util.Log throws in plain JVM tests.
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
    fun `significantDailyUsage keeps a minute of use but drops zero-minute apps`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            usageRepository.setDailyUsage(
                today,
                AppUsageSummary(packageName = "com.zero", label = "Zero", totalMinutes = 0, limitMinutes = null),
                AppUsageSummary(packageName = "com.one", label = "One", totalMinutes = 1, limitMinutes = null),
                AppUsageSummary(packageName = "com.real", label = "Real", totalMinutes = 15, limitMinutes = null),
            )

            val viewModel = createViewModel()

            assertEquals(
                listOf("com.one", "com.real"),
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
    fun `significantWeeklyUsage drops zero-minute apps this week`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            usageRepository.setWeeklyAppBreakdown(
                AppUsageSummary(packageName = "com.real", label = "Real", totalMinutes = 300, limitMinutes = null),
                AppUsageSummary(packageName = "com.one", label = "One", totalMinutes = 1, limitMinutes = null),
                AppUsageSummary(packageName = "com.zero", label = "Zero", totalMinutes = 0, limitMinutes = null),
            )

            val viewModel = createViewModel()

            assertEquals(
                listOf("com.real", "com.one"),
                viewModel.state.value.significantWeeklyUsage
                    .map { it.packageName },
            )
        }

    @Test
    fun `screen time uses the day's full total, not just the listed apps`() =
        runTest {
            val today = LocalDate.of(2026, 1, 15)
            clockProvider.setNow(today.atStartOfDay())
            // 40 minutes across listed apps, plus sub-minute opens the per-app list leaves out.
            usageRepository.setDailyUsage(
                today,
                AppUsageSummary(packageName = "com.app", label = "App", totalMinutes = 40, limitMinutes = null),
            )
            usageRepository.setWeeklyTotals(DailyTotal(today, 41))

            val viewModel = createViewModel()

            assertEquals(today, viewModel.state.value.today)
            assertEquals(41, viewModel.state.value.totalMinutesToday)
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

            assertFalse(viewModel.state.value.systemHealth.vpnServiceActive)
            // The VPN is optional — it being off doesn't mean blocking is down.
            assertTrue(viewModel.state.value.systemHealth.isBlockingActive)
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

    @Test
    fun `screen time falls back to the per-app sum when there's no daily total for today`() {
        val today = LocalDate.of(2026, 1, 15)
        val state =
            HomeState(
                today = today,
                dailyUsage = listOf(AppUsageSummary("com.a", "A", totalMinutes = 20, limitMinutes = null)),
                weeklyTotals = listOf(DailyTotal(today.minusDays(1), 99)),
            )
        assertEquals(20, state.totalMinutesToday)
        assertEquals(20, state.copy(today = null).totalMinutesToday)
    }
}
