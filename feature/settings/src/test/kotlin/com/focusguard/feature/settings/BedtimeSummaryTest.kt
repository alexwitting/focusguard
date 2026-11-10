package com.focusguard.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BedtimeSummaryTest {
    private fun edit(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ) = BedtimeEdit(startHour, startMinute, endHour, endMinute)

    @Test
    fun `overnight window counts hours across midnight`() {
        assertEquals("9 hours · ends the next morning", bedtimeSummary(edit(22, 0, 7, 0)))
    }

    @Test
    fun `same-day window with minutes`() {
        assertEquals("1 h 30 min · ends the same day", bedtimeSummary(edit(13, 0, 14, 30)))
    }

    @Test
    fun `singular hour and minutes-only lengths`() {
        assertEquals("1 hour · ends the same day", bedtimeSummary(edit(9, 0, 10, 0)))
        assertEquals("45 min · ends the next morning", bedtimeSummary(edit(23, 30, 0, 15)))
    }

    @Test
    fun `identical start and end is called out`() {
        assertEquals("Starts and ends at the same time", bedtimeSummary(edit(22, 0, 22, 0)))
    }
}
