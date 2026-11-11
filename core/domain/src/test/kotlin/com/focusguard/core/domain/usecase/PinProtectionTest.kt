package com.focusguard.core.domain.usecase

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.PinAttemptResult
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.usecase.block.DeleteAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.DeleteWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.GetAllAppsWithRulesUseCase
import com.focusguard.core.domain.usecase.block.GetContentFiltersUseCase
import com.focusguard.core.domain.usecase.block.GetWebBlockRulesUseCase
import com.focusguard.core.domain.usecase.block.SetContentFilterUseCase
import com.focusguard.core.domain.usecase.block.UpsertAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.UpsertWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.pin.DisablePinUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.SetPinUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.settings.GetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.GetGracePeriodEnabledUseCase
import com.focusguard.core.domain.usecase.settings.SetBedtimeUseCase
import com.focusguard.core.domain.usecase.settings.SetGracePeriodEnabledUseCase
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakePinRepository
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeWebBlockRuleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime

/**
 * Every rule and setting change through its use case, against in-memory repositories: open
 * without a PIN, gated once one is set, and unlocked for five minutes by a correct entry.
 */
class PinProtectionTest {
    private val clock = FakeClockProvider().apply { setNow(LocalDateTime.of(2026, 1, 15, 12, 0)) }
    private val pins = FakePinRepository()
    private val appRules = FakeBlockRuleRepository()
    private val webRules = FakeWebBlockRuleRepository()
    private val settings = FakeSettingsRepository()

    private val setPin = SetPinUseCase(pins)
    private val verifyPin = VerifyPinUseCase(pins, clock)
    private val disablePin = DisablePinUseCase(pins, clock)
    private val pinEnabled = GetPinEnabledUseCase(pins)

    private val rule = AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.TimeLimit(30))
    private val site = WebBlockRule(domain = "example.com")
    private val bedtime = TimeWindow(22, 0, 7, 0)

    /** One gated change: [apply] makes it with a token, [applied] reads back whether it took. */
    private class Change(
        val name: String,
        val apply: suspend (PinVerificationToken?) -> Unit,
        val applied: suspend () -> Boolean,
    )

    private val changes =
        listOf(
            Change(
                "add app rule",
                { UpsertAppBlockRuleUseCase(appRules, pins, clock)(rule, it) },
                { GetAllAppsWithRulesUseCase(appRules)().first().any { app -> app.rule == rule } },
            ),
            Change(
                "add website",
                { UpsertWebBlockRuleUseCase(webRules, pins, clock)(site, it) },
                { site in GetWebBlockRulesUseCase(webRules)().first() },
            ),
            Change(
                "turn on a content filter",
                { SetContentFilterUseCase(webRules, pins, clock)(ContentFilter.SHORTS, true, it) },
                { ContentFilter.SHORTS in GetContentFiltersUseCase(webRules)().first() },
            ),
            Change(
                "set bedtime",
                { SetBedtimeUseCase(settings, pins, clock)(bedtime, it) },
                { GetBedtimeUseCase(settings)().first() == bedtime },
            ),
            Change(
                "allow extensions",
                { SetGracePeriodEnabledUseCase(settings, pins, clock)(true, it) },
                { GetGracePeriodEnabledUseCase(settings)().first() },
            ),
        )

    private suspend fun correctPinToken(): PinVerificationToken = (verifyPin("123456") as PinAttemptResult.Success).token

    @Test
    fun `without a PIN every change applies with no token`() =
        runTest {
            assertFalse(pinEnabled().first())
            for (change in changes) {
                change.apply(null)
                assertTrue(change.applied(), change.name)
            }
        }

    @Test
    fun `with a PIN set every change is refused without a token`() =
        runTest {
            setPin("123456")
            assertTrue(pinEnabled().first())

            for (change in changes) {
                assertThrows<IllegalArgumentException>(change.name) { change.apply(null) }
                assertFalse(change.applied(), change.name)
            }
        }

    @Test
    fun `a correct PIN unlocks changes for five minutes only`() =
        runTest {
            setPin("123456")
            val token = correctPinToken()

            clock.setNow(LocalDateTime.of(2026, 1, 15, 12, 4, 59))
            for (change in changes) {
                change.apply(token)
                assertTrue(change.applied(), change.name)
            }

            clock.setNow(LocalDateTime.of(2026, 1, 15, 12, 5))
            assertThrows<IllegalArgumentException> { UpsertAppBlockRuleUseCase(appRules, pins, clock)(rule, token) }
        }

    @Test
    fun `removing rules is gated the same way`() =
        runTest {
            appRules.setRules(rule)
            webRules.setRules(site)
            setPin("123456")

            assertThrows<IllegalArgumentException> { DeleteAppBlockRuleUseCase(appRules, pins, clock)("com.app", null) }
            assertThrows<IllegalArgumentException> { DeleteWebBlockRuleUseCase(webRules, pins, clock)("example.com", null) }

            val token = correctPinToken()
            DeleteAppBlockRuleUseCase(appRules, pins, clock)("com.app", token)
            DeleteWebBlockRuleUseCase(webRules, pins, clock)("example.com", token)
            assertTrue(GetAllAppsWithRulesUseCase(appRules)().first().isEmpty())
            assertTrue(GetWebBlockRulesUseCase(webRules)().first().isEmpty())
        }

    @Test
    fun `turning the PIN off needs an unexpired token, after which changes need none`() =
        runTest {
            setPin("123456")
            val token = correctPinToken()

            clock.setNow(LocalDateTime.of(2026, 1, 15, 12, 6))
            assertThrows<IllegalArgumentException> { disablePin(token) }
            assertTrue(pinEnabled().first())

            disablePin(correctPinToken())
            assertFalse(pinEnabled().first())
            changes.first().apply(null)
            assertTrue(changes.first().applied())
        }

    @ParameterizedTest
    @ValueSource(strings = ["12345", "1234567", "12a456", ""])
    fun `a new PIN must be exactly six digits`(pin: String) =
        runTest {
            assertThrows<IllegalArgumentException> { setPin(pin) }
            assertFalse(pinEnabled().first())
        }

    @Test
    fun `clearing bedtime is a gated change too`() =
        runTest {
            settings.setBedtime(bedtime)
            setPin("123456")

            assertThrows<IllegalArgumentException> { SetBedtimeUseCase(settings, pins, clock)(null, null) }
            SetBedtimeUseCase(settings, pins, clock)(null, correctPinToken())
            assertEquals(null, GetBedtimeUseCase(settings)().first())
        }
}
