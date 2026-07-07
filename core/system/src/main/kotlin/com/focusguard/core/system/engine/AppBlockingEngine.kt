package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockDecision
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import java.time.LocalDateTime
import javax.inject.Inject

class AppBlockingEngine
    @Inject
    constructor() {
        fun decide(
            rule: AppBlockRule,
            now: LocalDateTime,
            usedMinutesToday: Long,
        ): BlockDecision {
            if (!rule.enabled) return BlockDecision.Allow

            return when (val strategy = rule.strategy) {
                is BlockingStrategy.AlwaysBlock ->
                    BlockDecision.Block(BlockReason.ALWAYS_BLOCKED)

                is BlockingStrategy.ScheduledBlock -> {
                    val inAllowedWindow = strategy.windows.any { isInWindow(it, now) }
                    if (inAllowedWindow) {
                        BlockDecision.Allow
                    } else {
                        BlockDecision.Block(BlockReason.OUTSIDE_ALLOWED_WINDOW)
                    }
                }

                is BlockingStrategy.TimeLimit -> {
                    if (usedMinutesToday >= strategy.dailyLimitMinutes) {
                        BlockDecision.Block(BlockReason.TIME_LIMIT_REACHED)
                    } else {
                        BlockDecision.Allow
                    }
                }
            }
        }

        private fun isInWindow(
            window: TimeWindow,
            now: LocalDateTime,
        ): Boolean {
            if (now.dayOfWeek !in window.days) return false
            val nowMins = now.hour * 60 + now.minute
            val startMins = window.startHour * 60 + window.startMinute
            val endMins = window.endHour * 60 + window.endMinute
            return if (startMins <= endMins) {
                nowMins in startMins..endMins
            } else {
                // window crosses midnight — allowed on the start-day side
                nowMins >= startMins || nowMins <= endMins
            }
        }
    }
