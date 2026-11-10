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
        enum class EventType {
            RESUMED,
            PAUSED,

            /** Screen turned off or device shutting down — every open session ends here. */
            SCREEN_OFF,
        }

        data class UsageEvent(
            val packageName: String,
            val type: EventType,
            val timestamp: Long,
        )

        /**
         * [sessions]: `(start, end)` windows per package. [stillOpen]: packages still in the
         * foreground at the window end — pass them back as `openAtStart` on the next poll so a
         * session that spans several polls keeps being counted.
         */
        data class SessionResult(
            val sessions: Map<String, List<Pair<Long, Long>>>,
            val stillOpen: Set<String>,
        )

        /**
         * Pairs [events] into usage sessions per package for the poll window
         * `[windowStart, windowEnd]`.
         *
         * Usage is polled in ~15-minute windows, but a session can span many of them (a 40-minute
         * video). Only pairing RESUMED→PAUSED *within* one window used to drop everything after
         * the first poll: the middle windows had no events at all, and the final PAUSED had no
         * matching RESUMED. So:
         * - [openAtStart] packages (the previous window's [SessionResult.stillOpen]) are treated
         *   as resumed at [windowStart];
         * - a PAUSED for a package with no earlier event in this window means it was open before
         *   the window started, so it counts from [windowStart] (when known);
         * - an app's activities are reference-counted, so switching activities inside one app
         *   (A resumes B before A pauses, or multi-window) neither ends nor double-counts it;
         * - [EventType.SCREEN_OFF] closes everything, so a missed PAUSED can't leave an app
         *   "open" across the night.
         *
         * [selfPackage] is excluded so FocusGuard's own foreground time is never counted.
         */
        fun pairSessions(
            events: List<UsageEvent>,
            windowEnd: Long,
            selfPackage: String,
            windowStart: Long? = null,
            openAtStart: Set<String> = emptySet(),
        ): SessionResult {
            val tracker = SessionTracker(windowStart)
            if (windowStart != null) {
                openAtStart.filter { it != selfPackage }.forEach { tracker.openAt(it, windowStart) }
            }
            events
                .filter { it.type == EventType.SCREEN_OFF || it.packageName != selfPackage }
                .forEach { event ->
                    when (event.type) {
                        EventType.RESUMED -> tracker.resume(event.packageName, event.timestamp)
                        EventType.PAUSED -> tracker.pause(event.packageName, event.timestamp)
                        EventType.SCREEN_OFF -> tracker.closeAll(event.timestamp)
                    }
                }
            val stillOpen = tracker.openPackages()
            tracker.closeAll(windowEnd)
            return SessionResult(tracker.sessions, stillOpen)
        }

        /** Mutable bookkeeping for one [pairSessions] call. */
        private class SessionTracker(
            private val windowStart: Long?,
        ) {
            private val openSince = mutableMapOf<String, Long>()
            private val openCount = mutableMapOf<String, Int>()
            private val seen = mutableSetOf<String>()
            val sessions = mutableMapOf<String, MutableList<Pair<Long, Long>>>()

            fun openAt(
                pkg: String,
                at: Long,
            ) {
                openSince[pkg] = at
                openCount[pkg] = 1
                seen += pkg
            }

            fun resume(
                pkg: String,
                at: Long,
            ) {
                openSince.putIfAbsent(pkg, at)
                openCount[pkg] = (openCount[pkg] ?: 0) + 1
                seen += pkg
            }

            fun pause(
                pkg: String,
                at: Long,
            ) {
                val count = openCount[pkg] ?: 0
                when {
                    count > 1 -> openCount[pkg] = count - 1
                    count == 1 -> close(pkg, at)
                    // First sign of this app in the window is a pause: it was open before the
                    // window started.
                    pkg !in seen && windowStart != null -> {
                        openSince[pkg] = windowStart
                        close(pkg, at)
                    }
                }
                seen += pkg
            }

            fun closeAll(at: Long) = openSince.keys.toList().forEach { close(it, at) }

            fun openPackages(): Set<String> = openSince.keys.toSet()

            private fun close(
                pkg: String,
                end: Long,
            ) {
                val start = openSince.remove(pkg) ?: return
                openCount.remove(pkg)
                if (end > start) sessions.getOrPut(pkg) { mutableListOf() }.add(start to end)
            }
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
