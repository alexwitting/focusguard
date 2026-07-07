package com.focusguard.core.domain.model

sealed class BlockingStrategy {
    data object AlwaysBlock : BlockingStrategy()

    data class ScheduledBlock(
        val windows: List<TimeWindow>,
    ) : BlockingStrategy()

    data class TimeLimit(
        val dailyLimitMinutes: Int,
    ) : BlockingStrategy()
}
