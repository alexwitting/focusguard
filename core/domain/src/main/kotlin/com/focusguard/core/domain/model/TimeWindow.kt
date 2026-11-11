package com.focusguard.core.domain.model

import java.time.DayOfWeek
import java.time.LocalDateTime

data class TimeWindow(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
) {
    init {
        require(startHour in 0..23) { "startHour must be 0–23" }
        require(startMinute in 0..59) { "startMinute must be 0–59" }
        require(endHour in 0..23) { "endHour must be 0–23" }
        require(endMinute in 0..59) { "endMinute must be 0–59" }
        require(days.isNotEmpty()) { "days must not be empty" }
    }

    /**
     * True when start and end are the same minute. Saving such a window is refused, since it
     * covers only that one minute; one already stored still loads.
     */
    val isZeroLength: Boolean get() = startHour == endHour && startMinute == endMinute

    /**
     * Whether [now] falls inside this window. Both ends are inclusive at minute precision.
     *
     * A window that crosses midnight (start after end, e.g. 22:00–07:00) belongs to the day it
     * *starts* on: Friday 22:00–07:00 covers Friday 23:00 and Saturday 01:00, but not Friday
     * 01:00 (that's Thursday night's window).
     */
    fun contains(now: LocalDateTime): Boolean {
        val nowMins = now.hour * MINUTES_PER_HOUR + now.minute
        val startMins = startHour * MINUTES_PER_HOUR + startMinute
        val endMins = endHour * MINUTES_PER_HOUR + endMinute
        return when {
            startMins <= endMins -> now.dayOfWeek in days && nowMins in startMins..endMins
            nowMins >= startMins -> now.dayOfWeek in days
            nowMins <= endMins -> now.dayOfWeek.minus(1) in days
            else -> false
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
