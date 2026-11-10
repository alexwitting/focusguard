package com.focusguard.feature.settings

/** "9 hours · ends the next morning", "1 h 30 min · ends the same day". */
internal fun bedtimeSummary(edit: BedtimeEdit): String {
    val start = edit.startHour * MINUTES_PER_HOUR + edit.startMinute
    val end = edit.endHour * MINUTES_PER_HOUR + edit.endMinute
    val minutes = (end - start + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    val length =
        when {
            minutes == 0 -> "Starts and ends at the same time"
            rest == 0 -> if (hours == 1) "1 hour" else "$hours hours"
            hours == 0 -> "$rest min"
            else -> "$hours h $rest min"
        }
    if (minutes == 0) return length
    return if (end <= start) "$length · ends the next morning" else "$length · ends the same day"
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
