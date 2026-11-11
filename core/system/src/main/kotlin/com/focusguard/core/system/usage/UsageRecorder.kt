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
 * Stores usage sessions from UsageStatsManager events since the last checkpoint. Called by
 * UsagePollerWorker and by the accessibility service before a time-limit check; a [Mutex] keeps
 * them from reading the same window twice.
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
         * Reads from the saved checkpoint rather than a fixed window, since WorkManager runs can be
         * late. The checkpoint and open-app set only advance after the sessions are saved.
         */
        private suspend fun record(windowEnd: Long) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val windowStart = prefs.getLong(KEY_LAST_PROCESSED_TIMESTAMP, windowEnd - FIRST_RUN_WINDOW_MS)
            if (windowEnd <= windowStart) return

            // After a long gap (device off, app killed) earlier open apps have certainly closed.
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

        // API 28 event types; they never occur on older devices.
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

        /** Splits a session at midnight so each day only counts its own part. */
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
            // Window used on the very first run, before there's a checkpoint.
            private const val FIRST_RUN_WINDOW_MS = 15 * 60 * 1000L
            private const val MAX_CARRY_OVER_GAP_MS = 6 * 60 * 60 * 1000L
            private const val PREFS_NAME = "focusguard_usage_poller_state"
            private const val KEY_LAST_PROCESSED_TIMESTAMP = "last_processed_timestamp"
            private const val KEY_OPEN_PACKAGES = "open_packages"
        }
    }
