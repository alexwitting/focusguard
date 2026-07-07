package com.focusguard

import app.cash.turbine.test
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.MainDispatcherExtension
import com.focusguard.navigation.HomeRoute
import com.focusguard.navigation.OnboardingRoute
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val settingsRepository = FakeSettingsRepository()

    @Test
    fun `routes to onboarding when onboarding is not complete`() =
        runTest {
            val viewModel = MainViewModel(settingsRepository)

            // stateIn's initial `null` is conflated away by the eager UnconfinedTestDispatcher
            // before a collector can observe it — only the settled value is guaranteed here.
            viewModel.startDestination.test {
                assertEquals(OnboardingRoute, awaitItem())
            }
        }

    @Test
    fun `routes to home when onboarding is complete`() =
        runTest {
            settingsRepository.setOnboardingComplete()
            val viewModel = MainViewModel(settingsRepository)

            viewModel.startDestination.test {
                assertEquals(HomeRoute, awaitItem())
            }
        }
}
