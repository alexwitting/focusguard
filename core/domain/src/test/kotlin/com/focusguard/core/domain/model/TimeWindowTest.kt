package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalDateTime

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

    // 2025-06-06 is a Friday.
    private fun friday(
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime.of(2025, 6, 6, hour, minute)

    @Test
    fun `same-day window contains its range inclusively on selected days only`() {
        val window = TimeWindow(9, 0, 17, 0, setOf(DayOfWeek.FRIDAY))
        assertTrue(window.contains(friday(9)))
        assertTrue(window.contains(friday(17)))
        assertFalse(window.contains(friday(8, 59)))
        assertFalse(window.contains(friday(17, 1)))
        assertFalse(window.contains(friday(12).plusDays(1)))
    }

    @Test
    fun `overnight window belongs to the day it starts on`() {
        val fridayNight = TimeWindow(22, 0, 7, 0, setOf(DayOfWeek.FRIDAY))
        assertTrue(fridayNight.contains(friday(23)))
        assertTrue(fridayNight.contains(friday(1).plusDays(1))) // Saturday 01:00
        assertFalse(fridayNight.contains(friday(1))) // Friday 01:00 is Thursday night
        assertFalse(fridayNight.contains(friday(12)))
    }

    @Test
    fun `overnight window on Sunday wraps into Monday morning`() {
        val sundayNight = TimeWindow(22, 0, 7, 0, setOf(DayOfWeek.SUNDAY))
        assertTrue(sundayNight.contains(friday(6).plusDays(3))) // Monday 06:00
        assertFalse(sundayNight.contains(friday(6).plusDays(2))) // Sunday 06:00
    }
}
