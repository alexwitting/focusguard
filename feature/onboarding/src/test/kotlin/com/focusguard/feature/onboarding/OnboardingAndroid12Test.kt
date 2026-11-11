package com.focusguard.feature.onboarding

import app.cash.turbine.test
import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeSystemHealthRepository
import com.focusguard.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** On Android 12+ onboarding adds the exact-alarm step before finishing. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [31])
class OnboardingAndroid12Test {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val systemHealth = FakeSystemHealthRepository()
    private val settings = FakeSettingsRepository()

    @Test
    fun `the full flow asks for exact alarms after the VPN, then finishes`() =
        runTest {
            val viewModel = OnboardingViewModel(systemHealth, settings)
            val steps = mutableListOf(viewModel.state.value.step)
            while (viewModel.state.value.step != OnboardingStep.DONE) {
                viewModel.onNext()
                steps += viewModel.state.value.step
            }
            assertEquals(
                listOf(
                    OnboardingStep.WELCOME,
                    OnboardingStep.ACCESSIBILITY,
                    OnboardingStep.USAGE_ACCESS,
                    OnboardingStep.BATTERY,
                    OnboardingStep.VPN,
                    OnboardingStep.EXACT_ALARM,
                    OnboardingStep.DONE,
                ),
                steps,
            )

            viewModel.effects.test {
                viewModel.onNext()
                assertEquals(OnboardingEffect.NavigateToMain, awaitItem())
            }
            assertTrue(settings.isOnboardingComplete().first())
        }

    @Test
    fun `the exact-alarm step opens its settings page and reads its permission`() =
        runTest {
            val viewModel = OnboardingViewModel(systemHealth, settings)
            while (viewModel.state.value.step != OnboardingStep.EXACT_ALARM) viewModel.onNext()
            assertFalse(viewModel.isCurrentStepGranted())

            viewModel.effects.test {
                viewModel.onGrant()
                assertEquals(OnboardingEffect.OpenExactAlarmSettings, awaitItem())
            }

            systemHealth.setPermissionsHealth(PermissionsHealth(false, false, false, false, exactAlarmAllowed = true))
            viewModel.forceRefresh()
            val deadline = System.currentTimeMillis() + 2_000
            while (!viewModel.isCurrentStepGranted() && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue(viewModel.isCurrentStepGranted())
        }
}
