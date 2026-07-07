package com.focusguard.core.domain.model

data class AppUsageSummary(
    val packageName: String,
    val label: String,
    val totalMinutes: Int,
    val limitMinutes: Int?,
)
