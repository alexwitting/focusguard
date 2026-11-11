package com.focusguard.core.system.engine

import javax.inject.Inject
import javax.inject.Singleton

/** Turns usage events into sessions and decides when to send the 80% time-limit warning. */
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
         * [sessions]: `(start, end)` per package. [stillOpen]: packages still in the foreground at
         * the window end; pass them as `openAtStart` to the next call.
         */
        data class SessionResult(
            val sessions: Map<String, List<Pair<Long, Long>>>,
            val stillOpen: Set<String>,
        )

        /**
         * Pairs [events] into sessions for the window `[windowStart, windowEnd]`:
         * - [openAtStart] packages count as resumed at [windowStart];
         * - a PAUSED with no earlier event for that package counts from [windowStart];
         * - activities are reference-counted, so moving between an app's own screens is one session;
         * - [EventType.SCREEN_OFF] closes every open session.
         * [selfPackage] is never counted.
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
                    // Paused before any resume in this window: it was open at the window start.
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

        /** True once usage is at 80–99% of [limitMinutes] and no warning has been sent today. */
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
