package com.focusguard.core.system.usage

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.UsageSession
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.system.engine.UsageSessionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads UsageStatsManager events since the last checkpoint and stores them as usage sessions.
 *
 * Shared by UsagePollerWorker (every ~15 minutes) and the accessibility service (right before a
 * time-limit decision, so the limit is checked against usage up to *now* rather than whatever
 * the last poll happened to record). A [Mutex] serialises the two so a window is never read
 * twice or skipped.
 */
@Singleton
class UsageRecorder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val usageRepository: UsageRepository,
        private val usageSessionEngine: UsageSessionEngine,
        private val clockProvider: ClockProvider,
    ) {
        private val mutex = Mutex()

        /** Records every session up to now. Safe to call concurrently. */
        suspend fun recordUpToNow() =
            mutex.withLock {
                withContext(Dispatchers.IO) { record(windowEnd = clockProvider.epochMillis()) }
            }

        /**
         * Windows from the last successfully-processed timestamp, not a fixed "now minus 15
         * minutes" — WorkManager's periodic interval is a minimum, not a guarantee, and real
         * executions can land later than that (Doze, standby-bucket throttling, OS scheduling
         * slack). A fixed rolling window would silently and permanently drop whatever usage
         * happened before its start once a gap exceeds it. The checkpoint (and the set of apps
         * still open at it) only advances after events are recorded, so a failed run retries the
         * same window rather than skipping it.
         */
        private suspend fun record(windowEnd: Long) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val windowStart = prefs.getLong(KEY_LAST_PROCESSED_TIMESTAMP, windowEnd - FIRST_RUN_WINDOW_MS)
            if (windowEnd <= windowStart) return

            // A long gap means the device was off or the app was killed; whatever was open then
            // has certainly closed since, and counting it as open for hours would be wrong.
            val openAtStart =
                if (windowEnd - windowStart > MAX_CARRY_OVER_GAP_MS) {
                    emptySet()
                } else {
                    prefs.getStringSet(KEY_OPEN_PACKAGES, emptySet()).orEmpty()
                }

            val events = usm.queryEvents(windowStart, windowEnd) ?: return
            val result =
                usageSessionEngine.pairSessions(
                    events = readEvents(events),
                    windowEnd = windowEnd,
                    selfPackage = context.packageName,
                    windowStart = windowStart,
                    openAtStart = openAtStart,
                )

            result.sessions.forEach { (pkg, pairs) ->
                pairs
                    .flatMap { (start, end) -> splitAtMidnight(start, end) }
                    .groupBy { (start, _) -> start.toLocalDate() }
                    .forEach { (date, pairsForDate) ->
                        usageRepository.recordUsageSession(
                            AppUsageRecord(
                                packageName = pkg,
                                date = date,
                                totalMinutes = 0,
                                sessions =
                                    pairsForDate.map { (s, e) ->
                                        UsageSession(startTime = Instant.ofEpochMilli(s), endTime = Instant.ofEpochMilli(e))
                                    },
                            ),
                        )
                    }
            }

            prefs
                .edit()
                .putLong(KEY_LAST_PROCESSED_TIMESTAMP, windowEnd)
                .putStringSet(KEY_OPEN_PACKAGES, result.stillOpen)
                .apply()
        }

        // SCREEN_NON_INTERACTIVE / DEVICE_SHUTDOWN are API 28 constants, but they're plain ints
        // and those events simply never appear on older devices.
        @SuppressLint("InlinedApi")
        private fun readEvents(events: UsageEvents): List<UsageSessionEngine.UsageEvent> {
            val event = UsageEvents.Event()
            val out = mutableListOf<UsageSessionEngine.UsageEvent>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type =
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> UsageSessionEngine.EventType.RESUMED
                        UsageEvents.Event.ACTIVITY_PAUSED -> UsageSessionEngine.EventType.PAUSED
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                        UsageEvents.Event.DEVICE_SHUTDOWN,
                        -> UsageSessionEngine.EventType.SCREEN_OFF
                        else -> continue
                    }
                out += UsageSessionEngine.UsageEvent(event.packageName.orEmpty(), type, event.timeStamp)
            }
            return out
        }

        private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

        /** A late-night session is split at midnight so each day's total only counts its own part. */
        private fun splitAtMidnight(
            start: Long,
            end: Long,
        ): List<Pair<Long, Long>> {
            val zone = ZoneId.systemDefault()
            val out = mutableListOf<Pair<Long, Long>>()
            var cursor = start
            while (cursor < end) {
                val nextMidnight =
                    cursor
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                val segmentEnd = minOf(end, nextMidnight)
                out += cursor to segmentEnd
                cursor = segmentEnd
            }
            return out
        }

        companion object {
            // Fallback window for the very first run only — see record()'s doc comment.
            private const val FIRST_RUN_WINDOW_MS = 15 * 60 * 1000L
            private const val MAX_CARRY_OVER_GAP_MS = 6 * 60 * 60 * 1000L
            private const val PREFS_NAME = "focusguard_usage_poller_state"
            private const val KEY_LAST_PROCESSED_TIMESTAMP = "last_processed_timestamp"
            private const val KEY_OPEN_PACKAGES = "open_packages"
        }
    }
