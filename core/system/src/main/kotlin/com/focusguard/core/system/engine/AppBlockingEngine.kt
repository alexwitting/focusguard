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
        /**
         * [bedtime], when set and [now] is inside it, blocks any enabled rule regardless of its
         * own strategy — bedtime is "everything I've chosen to restrict is off-limits overnight".
         */
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
         * Looks ahead minute by minute (up to [horizonMinutes]) for the first moment [rule] and
         * [bedtime] block the app, so a block can fire *while* the app is open — e.g. bedtime
         * starting at 22:00 mid-scroll, or an allowed-hours window closing. Without this, both
         * only took effect the next time the user switched into the app.
         *
         * Usage is ignored (time limits have their own countdown), so this only ever reports
         * schedule- or bedtime-driven blocks. Returns null if nothing blocks within the horizon.
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
