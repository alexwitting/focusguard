package com.focusguard.core.system.engine

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockDecision
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class AppBlockingEngine
    @Inject
    constructor() {
        /** Inside [bedtime], every enabled rule blocks regardless of its strategy. */
        fun decide(
            rule: AppBlockRule,
            now: LocalDateTime,
            usedMinutesToday: Long,
            bedtime: TimeWindow? = null,
        ): BlockDecision {
            if (!rule.enabled) return BlockDecision.Allow
            if (bedtime != null && bedtime.contains(now)) return BlockDecision.Block(BlockReason.BEDTIME)

            return when (val strategy = rule.strategy) {
                is BlockingStrategy.AlwaysBlock ->
                    BlockDecision.Block(BlockReason.ALWAYS_BLOCKED)

                is BlockingStrategy.ScheduledBlock -> {
                    val inAllowedWindow = strategy.windows.any { it.contains(now) }
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

        /** When an app that's allowed right now will next be blocked, and why. */
        data class UpcomingBlock(
            val delay: Duration,
            val reason: BlockReason,
        )

        /**
         * The first moment within [horizonMinutes] that [rule] or [bedtime] blocks the app, so a
         * block can fire while it's open. Usage is ignored; time limits have their own alarm.
         */
        fun nextBlock(
            rule: AppBlockRule,
            now: LocalDateTime,
            bedtime: TimeWindow?,
            horizonMinutes: Int = MINUTES_PER_DAY,
        ): UpcomingBlock? {
            if (!rule.enabled) return null
            val firstMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            for (offset in 0 until horizonMinutes) {
                val at = firstMinute.plusMinutes(offset.toLong())
                val decision = decide(rule, at, usedMinutesToday = 0, bedtime = bedtime)
                if (decision is BlockDecision.Block) return UpcomingBlock(Duration.between(now, at), decision.reason)
            }
            return null
        }

        private companion object {
            const val MINUTES_PER_DAY = 24 * 60
        }
    }
