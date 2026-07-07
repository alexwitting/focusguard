package com.focusguard.core.system.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin — no Android imports. Pairs raw foreground/background events into usage
 * sessions and decides when a time-limit warning notification should fire.
 */
@Singleton
class UsageSessionEngine
    @Inject
    constructor() {
        enum class EventType { RESUMED, PAUSED }

        data class UsageEvent(
            val packageName: String,
            val type: EventType,
            val timestamp: Long,
        )

        /**
         * Pairs [events] into `(start, end)` session windows per package. A package still
         * resumed at [windowEnd] (no matching PAUSED event yet) gets one session closed at
         * [windowEnd] — it's still in the foreground, so the poll captures partial usage
         * rather than losing the session entirely. [selfPackage] is excluded so FocusGuard's
         * own foreground time is never counted.
         */
        fun pairSessions(
            events: List<UsageEvent>,
            windowEnd: Long,
            selfPackage: String,
        ): Map<String, List<Pair<Long, Long>>> {
            val resumeTimes = mutableMapOf<String, Long>()
            val sessionPairs = mutableMapOf<String, MutableList<Pair<Long, Long>>>()

            for (event in events) {
                if (event.packageName == selfPackage) continue
                when (event.type) {
                    EventType.RESUMED -> resumeTimes[event.packageName] = event.timestamp
                    EventType.PAUSED -> {
                        resumeTimes.remove(event.packageName)?.let { startMs ->
                            sessionPairs.getOrPut(event.packageName) { mutableListOf() }.add(startMs to event.timestamp)
                        }
                    }
                }
            }

            resumeTimes.forEach { (pkg, startMs) ->
                sessionPairs.getOrPut(pkg) { mutableListOf() }.add(startMs to windowEnd)
            }

            return sessionPairs
        }

        /**
         * Whether a time-limit warning should fire: usage has crossed 80% of [limitMinutes]
         * but not yet reached 100% (a full block is handled elsewhere), and this package
         * hasn't already been warned today ([alreadyNotified]).
         */
        fun shouldWarn(
            usedMinutes: Long,
            limitMinutes: Long,
            alreadyNotified: Boolean,
        ): Boolean {
            if (limitMinutes <= 0 || alreadyNotified) return false
            val percent = (usedMinutes * 100) / limitMinutes
            return percent in 80..99
        }
    }
