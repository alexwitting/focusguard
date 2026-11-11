package com.focusguard.core.domain.usecase

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.usecase.block.UpsertAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.settings.SetBedtimeUseCase
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakePinRepository
import com.focusguard.core.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Windows that start and end at the same minute are refused wherever they're saved. */
class RuleValidationTest {
    private val clock = FakeClockProvider()
    private val pins = FakePinRepository()
    private val rules = FakeBlockRuleRepository()
    private val settings = FakeSettingsRepository()
    private val upsertRule = UpsertAppBlockRuleUseCase(rules, pins, clock)
    private val setBedtime = SetBedtimeUseCase(settings, pins, clock)

    @Test
    fun `allowed hours that start and end together aren't saved`() =
        runTest {
            val rule = AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.ScheduledBlock(listOf(TimeWindow(9, 0, 9, 0))))

            val error = assertThrows<IllegalArgumentException> { upsertRule(rule, null) }

            assertEquals("Allowed hours need different start and end times", error.message)
            assertNull(rules.getAppBlockRule("com.app").first())
        }

    @Test
    fun `a bedtime that starts and ends together isn't saved, and clearing bedtime still works`() =
        runTest {
            settings.setBedtime(TimeWindow(22, 0, 7, 0))

            assertThrows<IllegalArgumentException> { setBedtime(TimeWindow(22, 0, 22, 0), null) }
            assertEquals(TimeWindow(22, 0, 7, 0), settings.getBedtime().first())

            setBedtime(null, null)
            assertNull(settings.getBedtime().first())
        }

    @Test
    fun `ordinary windows, including overnight ones, save normally`() =
        runTest {
            upsertRule(AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.ScheduledBlock(listOf(TimeWindow(22, 0, 6, 0)))), null)
            setBedtime(TimeWindow(23, 30, 23, 29), null)

            assertTrue(rules.getAppBlockRule("com.app").first() != null)
            assertEquals(TimeWindow(23, 30, 23, 29), settings.getBedtime().first())
        }
}
