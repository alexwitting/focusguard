package com.focusguard.core.domain.model

import java.time.DayOfWeek

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
}
