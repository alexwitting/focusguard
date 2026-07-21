package com.focusguard.core.system.foreground

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusguard.core.system.engine.WebContentFilterEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Backup foreground-app signal for website blocking, built on the same UsageStatsManager API
 * Android's own Digital Wellbeing uses. The accessibility service's AccessibilityEvent stream is
 * the primary, immediate trigger for a content scan; this exists only to catch the rare case
 * where a browser delays or throttles those events, so a blocked page doesn't sit on screen
 * unnoticed. Requires PACKAGE_USAGE_STATS, which the app already requests for time-limit
 * tracking (surfaced as the "Usage access" row in Settings) — no new permission needed.
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
                // The OS emits MOVE_TO_FOREGROUND/BACKGROUND on API < 29 and ACTIVITY_RESUMED/
                // PAUSED on API 29+ (the latter pair replaced the former for better multi-window
                // handling) — never both. minSdk here is 26, so both must be handled or this
                // silently stops detecting anything on Android 8/9. The MOVE_TO_* constants are
                // deprecated only as a recommendation to prefer ACTIVITY_* on newer APIs, not
                // because they stop working — suppressed below rather than dropped.
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
