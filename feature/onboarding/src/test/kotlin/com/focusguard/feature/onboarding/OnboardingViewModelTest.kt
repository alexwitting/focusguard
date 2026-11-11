package com.focusguard.feature.onboarding

import app.cash.turbine.test
import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.repository.SystemHealthRepository
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeSystemHealthRepository
import com.focusguard.core.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OnboardingViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val systemHealthRepository = FakeSystemHealthRepository()
    private val settingsRepository = FakeSettingsRepository()

    private fun createViewModel(): OnboardingViewModel = OnboardingViewModel(systemHealthRepository, settingsRepository)

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

    /** Polls for up to 2 seconds: forceRefresh runs on Dispatchers.IO, which tests don't replace. */
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        var result = condition()
        while (!result && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            result = condition()
        }
        assertTrue(result, "condition not met within timeout")
    }

    @Test
    fun `forceRefresh updates health from a fresh check`() =
        runTest {
            val viewModel = createViewModel()
            systemHealthRepository.setPermissionsHealth(
                PermissionsHealth(
                    accessibilityEnabled = true,
                    vpnPrepared = true,
                    usageStatsGranted = true,
                    batteryOptimizationIgnored = true,
                    exactAlarmAllowed = true,
                ),
            )

            viewModel.forceRefresh()

            awaitCondition { viewModel.state.value.health.accessibilityEnabled }
        }

    @Test
    fun `forceRefresh failure is caught and logged instead of crashing`() =
        runTest {
            val failingRepository =
                mockk<SystemHealthRepository> {
                    every { observePermissionsHealth() } returns systemHealthRepository.observePermissionsHealth()
                    coEvery { checkPermissionsNow() } throws IllegalStateException("boom")
                }
            val viewModel = OnboardingViewModel(failingRepository, settingsRepository)

            viewModel.forceRefresh()

            // State keeps its last value when the refresh fails.
            Thread.sleep(100)
            assertFalse(viewModel.state.value.health.accessibilityEnabled)
        }

    @Test
    fun `onNext advances through the step sequence`() =
        runTest {
            val viewModel = createViewModel()
            assertEquals(OnboardingStep.WELCOME, viewModel.state.value.step)

            viewModel.onNext()
            assertEquals(OnboardingStep.ACCESSIBILITY, viewModel.state.value.step)

            viewModel.onNext()
            assertEquals(OnboardingStep.USAGE_ACCESS, viewModel.state.value.step)
        }

    @Test
    fun `completing the final step marks onboarding complete and emits NavigateToMain`() =
        runTest {
            val viewModel = createViewModel()
            // Build.VERSION.SDK_INT is 0 in JVM tests, so step until DONE rather than counting.
            while (viewModel.state.value.step != OnboardingStep.DONE) {
                viewModel.onNext()
            }

            viewModel.effects.test {
                viewModel.onNext()
                assertEquals(OnboardingEffect.NavigateToMain, awaitItem())
            }
            assertTrue(settingsRepository.isOnboardingComplete().first())
        }

    @Test
    fun `isCurrentStepGranted reflects the current step's permission`() =
        runTest {
            systemHealthRepository.setPermissionsHealth(
                PermissionsHealth(
                    accessibilityEnabled = true,
                    vpnPrepared = false,
                    usageStatsGranted = false,
                    batteryOptimizationIgnored = false,
                    exactAlarmAllowed = false,
                ),
            )
            val viewModel = createViewModel()
            viewModel.onNext() // WELCOME -> ACCESSIBILITY

            assertTrue(viewModel.isCurrentStepGranted())
        }

    @Test
    fun `isCurrentStepGranted is true at WELCOME and DONE regardless of permissions`() =
        runTest {
            val viewModel = createViewModel()

            assertTrue(viewModel.isCurrentStepGranted()) // WELCOME

            while (viewModel.state.value.step != OnboardingStep.DONE) viewModel.onNext()
            assertTrue(viewModel.isCurrentStepGranted()) // DONE
        }

    @Test
    fun `isCurrentStepGranted checks the matching permission at every step`() =
        runTest {
            systemHealthRepository.setPermissionsHealth(
                PermissionsHealth(
                    accessibilityEnabled = false,
                    vpnPrepared = false,
                    usageStatsGranted = true,
                    batteryOptimizationIgnored = false,
                    exactAlarmAllowed = false,
                ),
            )
            val viewModel = createViewModel()
            viewModel.onNext() // -> ACCESSIBILITY
            viewModel.onNext() // -> USAGE_ACCESS

            assertTrue(viewModel.isCurrentStepGranted())

            viewModel.onNext() // -> BATTERY
            assertFalse(viewModel.isCurrentStepGranted())
        }

    @Test
    fun `onGrant sends the matching effect and records the step as attempted`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onNext() // -> ACCESSIBILITY

            viewModel.effects.test {
                viewModel.onGrant()
                assertEquals(OnboardingEffect.OpenAccessibilitySettings, awaitItem())
            }
            assertTrue(OnboardingStep.ACCESSIBILITY in viewModel.state.value.attemptedSteps)
        }

    @Test
    fun `onOpenAppInfo sends OpenAppDetailsSettings without changing the step`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onNext() // -> ACCESSIBILITY

            viewModel.effects.test {
                viewModel.onOpenAppInfo()
                assertEquals(OnboardingEffect.OpenAppDetailsSettings, awaitItem())
            }
            assertEquals(OnboardingStep.ACCESSIBILITY, viewModel.state.value.step)
        }

    @Test
    fun `isGranted on state matches each step's permission flag`() {
        val state =
            OnboardingState(
                health =
                    PermissionsHealth(
                        accessibilityEnabled = true,
                        vpnPrepared = false,
                        usageStatsGranted = true,
                        batteryOptimizationIgnored = false,
                        exactAlarmAllowed = true,
                    ),
            )
        assertTrue(state.isGranted(OnboardingStep.ACCESSIBILITY))
        assertFalse(state.isGranted(OnboardingStep.VPN))
        assertTrue(state.isGranted(OnboardingStep.USAGE_ACCESS))
        assertFalse(state.isGranted(OnboardingStep.BATTERY))
        assertTrue(state.isGranted(OnboardingStep.EXACT_ALARM))
        assertTrue(state.isGranted(OnboardingStep.WELCOME))
        assertTrue(state.isGranted(OnboardingStep.DONE))
    }

    @Test
    fun `onGrant sends the matching effect for every grantable step reachable under test`() =
        runTest {
            // EXACT_ALARM needs Android 12+, and SDK_INT is 0 in JVM tests, so it's never reached.
            val viewModel = createViewModel()
            val expected =
                mapOf(
                    OnboardingStep.ACCESSIBILITY to OnboardingEffect.OpenAccessibilitySettings,
                    OnboardingStep.USAGE_ACCESS to OnboardingEffect.OpenUsageAccessSettings,
                    OnboardingStep.BATTERY to OnboardingEffect.RequestBatteryOptimization,
                    OnboardingStep.VPN to OnboardingEffect.RequestVpnPrepare,
                )

            for ((step, effect) in expected) {
                while (viewModel.state.value.step != step) viewModel.onNext()
                viewModel.effects.test {
                    viewModel.onGrant()
                    assertEquals(effect, awaitItem())
                }
            }
        }

    @Test
    fun `onGrant at WELCOME records the step as attempted but sends no effect`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.effects.test {
                viewModel.onGrant()
                expectNoEvents()
            }
            assertTrue(OnboardingStep.WELCOME in viewModel.state.value.attemptedSteps)
        }

    @Test
    fun `onSkip advances to the next step just like onNext`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onSkip()

            assertEquals(OnboardingStep.ACCESSIBILITY, viewModel.state.value.step)
        }
}
