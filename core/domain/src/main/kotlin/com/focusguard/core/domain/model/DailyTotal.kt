package com.focusguard.core.domain.model

import java.time.LocalDate

data class DailyTotal(
    val date: LocalDate,
    val totalMinutes: Int,
)
