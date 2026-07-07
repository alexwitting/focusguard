package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek

class TimeWindowTest {
    @Test
    fun `defaults to all days of week`() {
        val window = TimeWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        assertEquals(DayOfWeek.entries.toSet(), window.days)
    }

    @Test
    fun `rejects invalid hour`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 24, startMinute = 0, endHour = 7, endMinute = 0)
        }
    }

    @Test
    fun `rejects invalid minute`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = 60, endHour = 7, endMinute = 0)
        }
    }

    @Test
    fun `rejects negative startHour`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = -1, startMinute = 0, endHour = 7, endMinute = 0)
        }
    }

    @Test
    fun `rejects negative startMinute`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = -1, endHour = 7, endMinute = 0)
        }
    }

    @Test
    fun `rejects invalid endHour`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = 0, endHour = 24, endMinute = 0)
        }
    }

    @Test
    fun `rejects negative endHour`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = 0, endHour = -1, endMinute = 0)
        }
    }

    @Test
    fun `rejects invalid endMinute`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = 60)
        }
    }

    @Test
    fun `rejects negative endMinute`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = -1)
        }
    }

    @Test
    fun `rejects empty days set`() {
        assertThrows<IllegalArgumentException> {
            TimeWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = 0, days = emptySet())
        }
    }

    @Test
    fun `allows single day`() {
        val window =
            TimeWindow(
                startHour = 9,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
                days = setOf(DayOfWeek.MONDAY),
            )
        assertEquals(setOf(DayOfWeek.MONDAY), window.days)
    }
}
