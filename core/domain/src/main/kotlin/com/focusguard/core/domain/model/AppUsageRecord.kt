package com.focusguard.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** Foreground sessions of one app on one day, as recorded from usage events. */
data class AppUsageRecord(
    val packageName: String,
    val date: LocalDate,
    val sessions: List<UsageSession>,
)

data class UsageSession(
    val startTime: Instant,
    val endTime: Instant,
)
