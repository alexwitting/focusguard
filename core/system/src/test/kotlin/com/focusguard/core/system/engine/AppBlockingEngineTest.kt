package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockDecision
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class AppBlockingEngineTest {
    private val engine = AppBlockingEngine()

    // --- disabled rule ---

    @Test
    fun `disabled rule always allows`() {
        val rule = rule(BlockingStrategy.AlwaysBlock, enabled = false)
        assertEquals(BlockDecision.Allow, engine.decide(rule, monday(14, 0), 999))
    }

    // --- AlwaysBlock ---

    @Test
    fun `AlwaysBlock returns ALWAYS_BLOCKED`() {
        val result = engine.decide(rule(BlockingStrategy.AlwaysBlock), monday(10, 0), 0)
        assertEquals(BlockDecision.Block(BlockReason.ALWAYS_BLOCKED), result)
    }

    // --- ScheduledBlock ---

    @Test
    fun `ScheduledBlock allows when now is inside window`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(12, 0), 0)
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `ScheduledBlock allows at exact window start`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(9, 0), 0)
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `ScheduledBlock allows at exact window end`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(17, 0), 0)
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `ScheduledBlock blocks when now is before window`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(8, 59), 0)
        assertEquals(BlockDecision.Block(BlockReason.OUTSIDE_ALLOWED_WINDOW), result)
    }

    @Test
    fun `ScheduledBlock blocks when now is after window`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(17, 1), 0)
        assertEquals(BlockDecision.Block(BlockReason.OUTSIDE_ALLOWED_WINDOW), result)
    }

    @Test
    fun `ScheduledBlock blocks when day is not in window`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(12, 0), 0)
        assertEquals(BlockDecision.Block(BlockReason.OUTSIDE_ALLOWED_WINDOW), result)
    }

    @Test
    fun `ScheduledBlock allows when any of multiple windows matches`() {
        val lunch = TimeWindow(12, 0, 13, 0, setOf(DayOfWeek.MONDAY))
        val evening = TimeWindow(18, 0, 20, 0, setOf(DayOfWeek.MONDAY))
        val result =
            engine.decide(
                rule(BlockingStrategy.ScheduledBlock(listOf(lunch, evening))),
                monday(18, 30),
                0,
            )
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `ScheduledBlock with midnight-crossing window allows after midnight on the following day`() {
        // Monday 22:00–06:00 runs into Tuesday morning — 02:00 Tuesday is still Monday night.
        val window = TimeWindow(22, 0, 6, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(2, 0).plusDays(1), 0)
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `ScheduledBlock with midnight-crossing window blocks the early hours of its own start day`() {
        // 02:00 Monday belongs to Sunday night's window, which isn't selected.
        val window = TimeWindow(22, 0, 6, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(2, 0), 0)
        assertEquals(BlockDecision.Block(BlockReason.OUTSIDE_ALLOWED_WINDOW), result)
    }

    @Test
    fun `ScheduledBlock with midnight-crossing window allows before midnight`() {
        val window = TimeWindow(22, 0, 6, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(23, 0), 0)
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `ScheduledBlock with midnight-crossing window blocks during mid-day`() {
        val window = TimeWindow(22, 0, 6, 0, setOf(DayOfWeek.MONDAY))
        val result = engine.decide(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(12, 0), 0)
        assertEquals(BlockDecision.Block(BlockReason.OUTSIDE_ALLOWED_WINDOW), result)
    }

    // --- TimeLimit ---

    @Test
    fun `TimeLimit allows when usage is below limit`() {
        val result = engine.decide(rule(BlockingStrategy.TimeLimit(60)), monday(10, 0), 59)
        assertEquals(BlockDecision.Allow, result)
    }

    @Test
    fun `TimeLimit blocks when usage equals limit`() {
        val result = engine.decide(rule(BlockingStrategy.TimeLimit(60)), monday(10, 0), 60)
        assertEquals(BlockDecision.Block(BlockReason.TIME_LIMIT_REACHED), result)
    }

    @Test
    fun `TimeLimit blocks when usage exceeds limit`() {
        val result = engine.decide(rule(BlockingStrategy.TimeLimit(30)), monday(10, 0), 120)
        assertEquals(BlockDecision.Block(BlockReason.TIME_LIMIT_REACHED), result)
    }

    @Test
    fun `TimeLimit allows with zero usage`() {
        val result = engine.decide(rule(BlockingStrategy.TimeLimit(60)), monday(10, 0), 0)
        assertEquals(BlockDecision.Allow, result)
    }

    // --- helpers ---

    // --- Bedtime ---

    @Test
    fun `bedtime blocks every enabled rule inside the window, whatever its strategy`() {
        val bedtime = TimeWindow(22, 0, 7, 0)
        val limit = rule(BlockingStrategy.TimeLimit(60))
        assertEquals(BlockDecision.Block(BlockReason.BEDTIME), engine.decide(limit, monday(23, 30), 0, bedtime))
        assertEquals(BlockDecision.Block(BlockReason.BEDTIME), engine.decide(limit, monday(6, 0), 0, bedtime))
    }

    @Test
    fun `outside bedtime the rule's own strategy decides`() {
        val bedtime = TimeWindow(22, 0, 7, 0)
        assertEquals(BlockDecision.Allow, engine.decide(rule(BlockingStrategy.TimeLimit(60)), monday(12, 0), 10, bedtime))
        assertEquals(
            BlockDecision.Block(BlockReason.ALWAYS_BLOCKED),
            engine.decide(rule(BlockingStrategy.AlwaysBlock), monday(12, 0), 0, bedtime),
        )
    }

    @Test
    fun `bedtime never blocks a disabled rule`() {
        val bedtime = TimeWindow(22, 0, 7, 0)
        assertEquals(BlockDecision.Allow, engine.decide(rule(BlockingStrategy.AlwaysBlock, enabled = false), monday(23, 0), 0, bedtime))
    }

    // --- nextBlock (blocks that start while the app is open) ---

    @Test
    fun `nextBlock finds when an allowed-hours window closes`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.MONDAY))
        val next = engine.nextBlock(rule(BlockingStrategy.ScheduledBlock(listOf(window))), monday(16, 30).plusSeconds(20), null)
        // Allowed through 17:00 inclusive, blocked from 17:01.
        assertEquals(
            java.time.Duration
                .ofMinutes(31)
                .minusSeconds(20),
            next?.delay,
        )
        assertEquals(BlockReason.OUTSIDE_ALLOWED_WINDOW, next?.reason)
    }

    @Test
    fun `nextBlock finds bedtime starting for a time-limited app`() {
        val bedtime = TimeWindow(22, 0, 7, 0)
        val next = engine.nextBlock(rule(BlockingStrategy.TimeLimit(60)), monday(21, 45), bedtime)
        assertEquals(java.time.Duration.ofMinutes(15), next?.delay)
        assertEquals(BlockReason.BEDTIME, next?.reason)
    }

    @Test
    fun `nextBlock is null when nothing blocks within the horizon`() {
        assertEquals(null, engine.nextBlock(rule(BlockingStrategy.TimeLimit(60)), monday(12, 0), null))
        assertEquals(null, engine.nextBlock(rule(BlockingStrategy.AlwaysBlock, enabled = false), monday(12, 0), null))
    }

    private fun rule(
        strategy: BlockingStrategy,
        enabled: Boolean = true,
    ) = AppBlockRule(
        packageName = "com.example.app",
        label = "Example",
        strategy = strategy,
        enabled = enabled,
    )

    private fun monday(
        hour: Int,
        minute: Int,
    ): LocalDateTime = LocalDateTime.of(2025, 6, 2, hour, minute) // 2025-06-02 is a Monday
}
