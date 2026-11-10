package com.focusguard.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.util.Locale

class FormattingTest {
    @Test
    fun `formatMinutes shows hours and minutes, dropping whichever part is zero`() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("45m", formatMinutes(45))
        assertEquals("2h", formatMinutes(120))
        assertEquals("3h 12m", formatMinutes(192))
    }

    @Test
    fun `formatClock zero-pads both parts`() {
        assertEquals("09:05", formatClock(9, 5))
        assertEquals("23:59", formatClock(23, 59))
    }

    @Test
    fun `describeDays names the common sets and lists the rest in week order`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            assertEquals("Every day", describeDays(DayOfWeek.entries.toSet()))
            assertEquals(
                "Weekdays",
                describeDays(DayOfWeek.entries.toSet() - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            )
            assertEquals("Weekends", describeDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)))
            assertEquals("Mon, Wed, Fri", describeDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
