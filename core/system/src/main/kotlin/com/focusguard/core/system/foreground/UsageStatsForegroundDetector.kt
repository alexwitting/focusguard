package com.focusguard.core.system.foreground

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusguard.core.system.engine.WebContentFilterEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Backup foreground signal for the browser scan, in case a browser delays or throttles
 * accessibility events. Uses the usage-access permission the app already has.
 */
class UsageStatsForegroundDetector
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** Package of the browser currently in the foreground, per UsageStatsManager, or null. */
        fun currentForegroundBrowser(lookbackMs: Long = DEFAULT_LOOKBACK_MS): String? {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - lookbackMs, end)

            var foreground: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // API 29+ reports ACTIVITY_RESUMED/PAUSED, older versions MOVE_TO_FOREGROUND/
                // BACKGROUND; minSdk 26 needs both.
                @Suppress("DEPRECATION")
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.ACTIVITY_RESUMED -> foreground = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_PAUSED ->
                        if (event.packageName == foreground) foreground = null
                }
            }
            return foreground?.takeIf { it in WebContentFilterEngine.KNOWN_BROWSERS }
        }

        companion object {
            private const val DEFAULT_LOOKBACK_MS = 3_000L
        }
    }
