package com.focusguard.core.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class AppUsageRecord(
    val packageName: String,
    val date: LocalDate,
    val totalMinutes: Int,
    val sessions: List<UsageSession>,
)

data class UsageSession(
    val startTime: Instant,
    val endTime: Instant,
) {
    val durationMinutes: Int get() = Duration.between(startTime, endTime).toMinutes().toInt()
}
