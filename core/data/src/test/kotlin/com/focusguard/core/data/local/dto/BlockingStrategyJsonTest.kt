package com.focusguard.core.data.local.dto

import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

class BlockingStrategyJsonTest {
    @Test
    fun `AlwaysBlock round-trips through JSON`() {
        val strategy: BlockingStrategy = BlockingStrategy.AlwaysBlock

        assertEquals(strategy, strategy.toJson().toBlockingStrategy())
    }

    @Test
    fun `TimeLimit round-trips through JSON`() {
        val strategy: BlockingStrategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 45)

        assertEquals(strategy, strategy.toJson().toBlockingStrategy())
    }

    @Test
    fun `ScheduledBlock round-trips through JSON, preserving each window's days`() {
        val strategy: BlockingStrategy =
            BlockingStrategy.ScheduledBlock(
                windows =
                    listOf(
                        TimeWindow(
                            startHour = 9,
                            startMinute = 0,
                            endHour = 17,
                            endMinute = 30,
                            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        ),
                    ),
            )

        assertEquals(strategy, strategy.toJson().toBlockingStrategy())
    }

    @Test
    fun `TimeWindow round-trips through JSON`() {
        val window = TimeWindow(startHour = 22, startMinute = 15, endHour = 6, endMinute = 45, days = setOf(DayOfWeek.SUNDAY))

        assertEquals(window, window.toJson().toTimeWindow())
    }
}
