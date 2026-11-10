package com.focusguard.feature.settings

import app.cash.turbine.test
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.usecase.pin.DisablePinUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.SetPinUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.settings.EnableWebContentBlockingUseCase
import com.focusguard.core.domain.usecase.settings.GetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.GetGracePeriodEnabledUseCase
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.settings.GetWebContentBlockingEnabledUseCase
import com.focusguard.core.domain.usecase.settings.SetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.SetGracePeriodEnabledUseCase
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakePinRepository
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeSystemHealthRepository
import com.focusguard.core.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.DayOfWeek

/**
 * Sociable tests: real [SettingsViewModel] + real UseCases wired to `Fake*Repository` doubles.
 * The PIN-gate mechanics themselves are covered thoroughly by BlockViewModelTest; these focus on
 * what's different here: the PIN enable/disable transition, and bedtime set/clear gating.
 */
class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val systemHealthRepository = FakeSystemHealthRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val pinRepository = FakePinRepository()

    private fun createViewModel(
        setBedtimeOverride: SetBedtimeUseCase? = null,
        disablePinOverride: DisablePinUseCase? = null,
        setPinOverride: SetPinUseCase? = null,
        enableWebContentBlockingOverride: EnableWebContentBlockingUseCase? = null,
    ): SettingsViewModel =
        SettingsViewModel(
            getSystemHealth = GetSystemHealthUseCase(systemHealthRepository),
            getBedtime = GetBedtimeUseCase(settingsRepository),
            setBedtime = setBedtimeOverride ?: SetBedtimeUseCase(settingsRepository, pinRepository, FakeClockProvider()),
            verifyPin = VerifyPinUseCase(pinRepository, FakeClockProvider()),
            setPin = setPinOverride ?: SetPinUseCase(pinRepository),
            disablePin = disablePinOverride ?: DisablePinUseCase(pinRepository, FakeClockProvider()),
            getPinEnabled = GetPinEnabledUseCase(pinRepository),
            getWebContentBlockingEnabled = GetWebContentBlockingEnabledUseCase(settingsRepository),
            enableWebContentBlocking = enableWebContentBlockingOverride ?: EnableWebContentBlockingUseCase(settingsRepository),
            getGracePeriodEnabled = GetGracePeriodEnabledUseCase(settingsRepository),
            setGracePeriodEnabled = SetGracePeriodEnabledUseCase(settingsRepository, pinRepository, FakeClockProvider()),
        )

    @Test
    fun `turning on the extension without a PIN saves it straight away`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.SetGracePeriodEnabled(true))
            advanceUntilIdle()

            assertTrue(settingsRepository.isGracePeriodEnabled().first())
            assertTrue(viewModel.state.value.gracePeriodEnabled)
        }

    @Test
    fun `turning on the extension with a PIN set asks for the PIN first, then saves`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetGracePeriodEnabled(true))
            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)
            assertFalse(settingsRepository.isGracePeriodEnabled().first())

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            advanceUntilIdle()

            assertTrue(settingsRepository.isGracePeriodEnabled().first())
            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
        }

    @Test
    fun `setting a PIN via RequestSetPin enables it`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.RequestSetPin)
            assertEquals(PinStep.EnterNew, viewModel.state.value.pinStep)

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            assertEquals(PinStep.ConfirmNew, viewModel.state.value.pinStep)
            assertFalse(viewModel.state.value.isPinEnabled)

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertTrue(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `a confirmation that doesn't match goes back to choosing a PIN and saves nothing`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.RequestSetPin)
            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            viewModel.onIntent(SettingsIntent.PinInputChanged("123457"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals(PinStep.EnterNew, viewModel.state.value.pinStep)
            assertEquals("PINs didn't match, try again", viewModel.state.value.pinError)
            assertFalse(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `disabling PIN with the correct old PIN turns it off`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.RequestDisablePin)
            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertFalse(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `repeated wrong PINs lock entry with a clear message`() =
        runTest {
            pinRepository.setPin("111111")
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.RequestDisablePin)

            repeat(4) {
                viewModel.onIntent(SettingsIntent.PinInputChanged("999999"))
                viewModel.onIntent(SettingsIntent.SubmitPin)
            }
            assertEquals("Incorrect PIN · 1 try left", viewModel.state.value.pinError)

            viewModel.onIntent(SettingsIntent.PinInputChanged("999999"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            assertEquals("Too many tries. Try again in 30s", viewModel.state.value.pinError)

            // Even the right PIN is refused during the lockout.
            viewModel.onIntent(SettingsIntent.PinInputChanged("111111"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            assertTrue(
                viewModel.state.value.pinError!!
                    .startsWith("Too many tries"),
            )
            assertTrue(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `disabling PIN with the wrong old PIN shows an error and PIN stays enabled`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.RequestDisablePin)
            viewModel.onIntent(SettingsIntent.PinInputChanged("000000"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals("Incorrect PIN", viewModel.state.value.pinError)
            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)
            assertTrue(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `SaveBedtime without PIN saves immediately and closes the dialog`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            viewModel.onIntent(SettingsIntent.SaveBedtime)

            assertFalse(viewModel.state.value.showBedtimeDialog)
            assertEquals(
                TimeWindow(22, 0, 7, 0),
                settingsRepository.getBedtime().first(),
            )
        }

    @Test
    fun `SaveBedtime with PIN enabled defers behind the PIN dialog then saves after the correct PIN`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            viewModel.onIntent(SettingsIntent.SaveBedtime)

            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)
            assertTrue(viewModel.state.value.showBedtimeDialog)
            assertNull(settingsRepository.getBedtime().first())

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertFalse(viewModel.state.value.showBedtimeDialog)
            assertEquals(TimeWindow(22, 0, 7, 0), settingsRepository.getBedtime().first())
        }

    @Test
    fun `ClearBedtime without PIN clears immediately`() =
        runTest {
            settingsRepository.setBedtime(TimeWindow(23, 0, 6, 0))
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.ClearBedtime)

            assertNull(settingsRepository.getBedtime().first())
        }

    @Test
    fun `ClearBedtime with PIN enabled defers behind the PIN dialog then clears after the correct PIN`() =
        runTest {
            pinRepository.setPin("123456")
            settingsRepository.setBedtime(TimeWindow(23, 0, 6, 0))
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.ClearBedtime)

            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)
            assertEquals(TimeWindow(23, 0, 6, 0), settingsRepository.getBedtime().first())

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertNull(settingsRepository.getBedtime().first())
        }

    @Test
    fun `RequestChangePin then the correct old PIN moves to EnterNew and the new PIN replaces it`() =
        runTest {
            pinRepository.setPin("111111")
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.RequestChangePin)
            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)

            viewModel.onIntent(SettingsIntent.PinInputChanged("111111"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            assertEquals(PinStep.EnterNew, viewModel.state.value.pinStep)

            viewModel.onIntent(SettingsIntent.PinInputChanged("222222"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            viewModel.onIntent(SettingsIntent.PinInputChanged("222222"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertTrue(pinRepository.verifyPin("222222"))
        }

    @Test
    fun `SubmitPin with fewer than 6 digits shows an error without verifying`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.RequestDisablePin)

            viewModel.onIntent(SettingsIntent.PinInputChanged("123"))
            viewModel.onIntent(SettingsIntent.SubmitPin)

            assertEquals("Enter a 6-digit PIN", viewModel.state.value.pinError)
            assertEquals(PinStep.VerifyOld, viewModel.state.value.pinStep)
            assertTrue(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `DismissPinDialog hides the dialog and clears input`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.RequestSetPin)
            viewModel.onIntent(SettingsIntent.PinInputChanged("12"))

            viewModel.onIntent(SettingsIntent.DismissPinDialog)

            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertEquals("", viewModel.state.value.pinInput)
        }

    @Test
    fun `ShowBedtimeDialog with no existing bedtime seeds the default edit`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            assertEquals(BedtimeEdit(), viewModel.state.value.bedtimeEdit)
        }

    @Test
    fun `ShowBedtimeDialog with an existing bedtime seeds the edit from it`() =
        runTest {
            settingsRepository.setBedtime(TimeWindow(21, 30, 5, 15, setOf(DayOfWeek.MONDAY)))
            val viewModel = createViewModel()
            // Let the init{} collector pick up the existing bedtime before opening the dialog.
            viewModel.state.first { it.bedtime != null }

            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            assertEquals(
                BedtimeEdit(startHour = 21, startMinute = 30, endHour = 5, endMinute = 15, days = setOf(DayOfWeek.MONDAY)),
                viewModel.state.value.bedtimeEdit,
            )
        }

    @Test
    fun `DismissBedtimeDialog hides the dialog`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            viewModel.onIntent(SettingsIntent.DismissBedtimeDialog)

            assertFalse(viewModel.state.value.showBedtimeDialog)
        }

    @Test
    fun `BedtimeStartChanged and BedtimeEndChanged update the edit`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            viewModel.onIntent(SettingsIntent.BedtimeStartChanged(hour = 20, minute = 15))
            viewModel.onIntent(SettingsIntent.BedtimeEndChanged(hour = 8, minute = 45))

            assertEquals(20, viewModel.state.value.bedtimeEdit.startHour)
            assertEquals(15, viewModel.state.value.bedtimeEdit.startMinute)
            assertEquals(8, viewModel.state.value.bedtimeEdit.endHour)
            assertEquals(45, viewModel.state.value.bedtimeEdit.endMinute)
        }

    @Test
    fun `BedtimeDayToggled removes a day when more than one remains selected`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            viewModel.onIntent(SettingsIntent.BedtimeDayToggled(DayOfWeek.MONDAY))

            assertFalse(DayOfWeek.MONDAY in viewModel.state.value.bedtimeEdit.days)
        }

    @Test
    fun `BedtimeDayToggled adds a day back once removed`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)
            viewModel.onIntent(SettingsIntent.BedtimeDayToggled(DayOfWeek.MONDAY))

            viewModel.onIntent(SettingsIntent.BedtimeDayToggled(DayOfWeek.MONDAY))

            assertTrue(DayOfWeek.MONDAY in viewModel.state.value.bedtimeEdit.days)
        }

    @Test
    fun `BedtimeDayToggled refuses to remove the last remaining day`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)
            DayOfWeek.entries.filterNot { it == DayOfWeek.FRIDAY }.forEach {
                viewModel.onIntent(SettingsIntent.BedtimeDayToggled(it))
            }
            assertEquals(setOf(DayOfWeek.FRIDAY), viewModel.state.value.bedtimeEdit.days)

            viewModel.onIntent(SettingsIntent.BedtimeDayToggled(DayOfWeek.FRIDAY))

            assertEquals(setOf(DayOfWeek.FRIDAY), viewModel.state.value.bedtimeEdit.days)
        }

    @Test
    fun `disabling PIN shows a snackbar and PIN stays enabled when the use case fails`() =
        runTest {
            pinRepository.setPin("123456")
            val disablePin =
                mockk<DisablePinUseCase> {
                    coEvery { this@mockk(any<PinVerificationToken>()) } throws IllegalStateException("boom")
                }
            val viewModel = createViewModel(disablePinOverride = disablePin)
            viewModel.onIntent(SettingsIntent.RequestDisablePin)

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
                viewModel.onIntent(SettingsIntent.SubmitPin)

                assertEquals(SettingsEffect.ShowSnackbar("Failed to disable PIN"), awaitItem())
            }
            assertTrue(viewModel.state.value.isPinEnabled)
        }

    @Test
    fun `SaveBedtime without PIN shows a snackbar when the use case fails`() =
        runTest {
            val setBedtime =
                mockk<SetBedtimeUseCase> {
                    coEvery { this@mockk(any(), any()) } throws IllegalStateException("save failed")
                }
            val viewModel = createViewModel(setBedtimeOverride = setBedtime)
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.SaveBedtime)

                assertEquals(SettingsEffect.ShowSnackbar("save failed"), awaitItem())
            }
        }

    @Test
    fun `ClearBedtime without PIN shows a snackbar when the use case fails`() =
        runTest {
            settingsRepository.setBedtime(TimeWindow(23, 0, 6, 0))
            val setBedtime =
                mockk<SetBedtimeUseCase> {
                    coEvery { this@mockk(any(), any()) } throws IllegalStateException("clear failed")
                }
            val viewModel = createViewModel(setBedtimeOverride = setBedtime)

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.ClearBedtime)

                assertEquals(SettingsEffect.ShowSnackbar("clear failed"), awaitItem())
            }
        }

    @Test
    fun `SaveBedtime with PIN shows a snackbar and keeps the dialog open when the use case fails`() =
        runTest {
            pinRepository.setPin("123456")
            val setBedtime =
                mockk<SetBedtimeUseCase> {
                    coEvery { this@mockk(any(), any()) } throws IllegalStateException("save failed")
                }
            val viewModel = createViewModel(setBedtimeOverride = setBedtime)
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)
            viewModel.onIntent(SettingsIntent.SaveBedtime)

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
                viewModel.onIntent(SettingsIntent.SubmitPin)

                assertEquals(SettingsEffect.ShowSnackbar("save failed"), awaitItem())
            }
            assertTrue(viewModel.state.value.showBedtimeDialog)
        }

    @Test
    fun `ClearBedtime with PIN shows a snackbar when the use case fails`() =
        runTest {
            pinRepository.setPin("123456")
            settingsRepository.setBedtime(TimeWindow(23, 0, 6, 0))
            val setBedtime =
                mockk<SetBedtimeUseCase> {
                    coEvery { this@mockk(any(), any()) } throws IllegalStateException("clear failed")
                }
            val viewModel = createViewModel(setBedtimeOverride = setBedtime)
            viewModel.onIntent(SettingsIntent.ClearBedtime)

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
                viewModel.onIntent(SettingsIntent.SubmitPin)

                assertEquals(SettingsEffect.ShowSnackbar("clear failed"), awaitItem())
            }
        }

    @Test
    fun `setting a new PIN shows a snackbar and stays on EnterNew when the use case fails`() =
        runTest {
            val setPin =
                mockk<SetPinUseCase> {
                    coEvery { this@mockk(any()) } throws IllegalStateException("set failed")
                }
            val viewModel = createViewModel(setPinOverride = setPin)
            viewModel.onIntent(SettingsIntent.RequestSetPin)

            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
                viewModel.onIntent(SettingsIntent.SubmitPin)

                assertEquals(SettingsEffect.ShowSnackbar("set failed"), awaitItem())
            }
            assertEquals(PinStep.EnterNew, viewModel.state.value.pinStep)
        }

    @Test
    fun `SaveBedtime with an invalid window shows a snackbar without ever reaching the PIN gate`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)
            viewModel.onIntent(SettingsIntent.BedtimeStartChanged(hour = 24, minute = 0))

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.SaveBedtime)

                assertEquals(SettingsEffect.ShowSnackbar("Invalid bedtime window"), awaitItem())
            }
            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
            assertTrue(viewModel.state.value.showBedtimeDialog)
        }

    @Test
    fun `SaveBedtime with an invalid window short-circuits before the PIN gate even when PIN is enabled`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(SettingsIntent.ShowBedtimeDialog)
            viewModel.onIntent(SettingsIntent.BedtimeStartChanged(hour = 24, minute = 0))

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.SaveBedtime)

                assertEquals(SettingsEffect.ShowSnackbar("Invalid bedtime window"), awaitItem())
            }
            assertEquals(PinStep.Hidden, viewModel.state.value.pinStep)
        }

    /**
     * forceRefresh launches on Dispatchers.IO explicitly, which MainDispatcherExtension doesn't
     * touch (it only swaps Dispatchers.Main) — same real-background-thread situation as
     * FocusGuardAccessibilityServiceTest's awaitCondition, so this polls with a bounded
     * real-time timeout instead of asserting immediately.
     */
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
    fun `forceRefresh updates system health and permissions immediately`() =
        runTest {
            val viewModel = createViewModel()
            systemHealthRepository.setSystemHealth(
                SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true),
            )

            viewModel.forceRefresh()

            awaitCondition { viewModel.state.value.systemHealth.accessibilityServiceActive }
        }

    @Test
    fun `forceRefresh waits afterDelayMs before refreshing`() =
        runTest {
            val viewModel = createViewModel()
            systemHealthRepository.setSystemHealth(
                SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true),
            )

            viewModel.forceRefresh(afterDelayMs = 50)

            awaitCondition { viewModel.state.value.systemHealth.accessibilityServiceActive }
        }

    @Test
    fun `EnableWebContentBlocking turns website content blocking on`() =
        runTest {
            val viewModel = createViewModel()
            assertFalse(viewModel.state.value.webContentBlockingEnabled)

            viewModel.onIntent(SettingsIntent.EnableWebContentBlocking)

            assertTrue(viewModel.state.value.webContentBlockingEnabled)
        }

    @Test
    fun `a failed browser-scan enable shows a snackbar and clears the spinner`() =
        runTest {
            val failing = mockk<EnableWebContentBlockingUseCase> { coEvery { this@mockk() } throws IllegalStateException("nope") }
            val viewModel = createViewModel(enableWebContentBlockingOverride = failing)

            viewModel.effects.test {
                viewModel.onIntent(SettingsIntent.EnableWebContentBlocking)
                assertEquals(SettingsEffect.ShowSnackbar("nope"), awaitItem())
            }
            assertFalse(viewModel.state.value.isEnablingWebContentBlocking)
        }

    @Test
    fun `a wrong PIN leaves the extension setting unchanged`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetGracePeriodEnabled(true))
            viewModel.onIntent(SettingsIntent.PinInputChanged("000000"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            advanceUntilIdle()

            assertEquals("Incorrect PIN", viewModel.state.value.pinError)
            assertFalse(settingsRepository.isGracePeriodEnabled().first())
        }

    @Test
    fun `turning the extension off also goes through the PIN when one is set`() =
        runTest {
            settingsRepository.setGracePeriodEnabled(true)
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(SettingsIntent.SetGracePeriodEnabled(false))
            viewModel.onIntent(SettingsIntent.PinInputChanged("123456"))
            viewModel.onIntent(SettingsIntent.SubmitPin)
            advanceUntilIdle()

            assertFalse(settingsRepository.isGracePeriodEnabled().first())
        }
}
