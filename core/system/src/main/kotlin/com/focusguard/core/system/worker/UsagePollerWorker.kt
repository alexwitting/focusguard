package com.focusguard.core.system.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusguard.core.domain.model.AppUsageRecord
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.UsageSession
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.system.engine.UsageSessionEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@HiltWorker
class UsagePollerWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val usageRepository: UsageRepository,
        private val blockRuleRepository: BlockRuleRepository,
        private val usageSessionEngine: UsageSessionEngine,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()

                recordSessions(usm, now)
                checkTimeLimits()

                Result.success()
            }

        /**
         * Windows from the last successfully-processed timestamp, not a fixed "now minus 15
         * minutes" — WorkManager's periodic interval is a minimum, not a guarantee, and real
         * executions can land later than that (Doze, standby-bucket throttling, OS scheduling
         * slack). A fixed rolling window would silently and permanently drop whatever usage
         * happened before its start once a gap exceeds it. The checkpoint only advances after
         * events are actually queried and recorded, so a failed run retries the same window
         * rather than skipping it.
         *
         * The window can span more than one calendar day if polling was delayed a long time, so
         * sessions are bucketed by each session's own start-time date rather than a single
         * blanket "today".
         */
        private suspend fun recordSessions(
            usm: UsageStatsManager,
            windowEnd: Long,
        ) {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val windowStart = prefs.getLong(KEY_LAST_PROCESSED_TIMESTAMP, windowEnd - WINDOW_MS)

            val events = usm.queryEvents(windowStart, windowEnd) ?: return
            val event = UsageEvents.Event()
            val rawEvents = mutableListOf<UsageSessionEngine.UsageEvent>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val type =
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> UsageSessionEngine.EventType.RESUMED
                        UsageEvents.Event.ACTIVITY_PAUSED -> UsageSessionEngine.EventType.PAUSED
                        else -> continue
                    }
                rawEvents.add(UsageSessionEngine.UsageEvent(pkg, type, event.timeStamp))
            }

            val sessionPairs = usageSessionEngine.pairSessions(rawEvents, windowEnd, applicationContext.packageName)

            sessionPairs.forEach { (pkg, pairs) ->
                pairs
                    .groupBy { (s, _) -> Instant.ofEpochMilli(s).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .forEach { (date, pairsForDate) ->
                        usageRepository.recordUsageSession(
                            AppUsageRecord(
                                packageName = pkg,
                                date = date,
                                totalMinutes = 0,
                                sessions =
                                    pairsForDate.map { (s, e) ->
                                        UsageSession(
                                            startTime = Instant.ofEpochMilli(s),
                                            endTime = Instant.ofEpochMilli(e),
                                        )
                                    },
                            ),
                        )
                    }
            }

            prefs.edit().putLong(KEY_LAST_PROCESSED_TIMESTAMP, windowEnd).apply()
        }

        private suspend fun checkTimeLimits() {
            val rules = blockRuleRepository.getAppBlockRules().first()

            for (rule in rules) {
                val strategy = rule.strategy
                if (strategy !is BlockingStrategy.TimeLimit) continue

                val usedMinutes = usageRepository.getTodayUsageMinutes(rule.packageName)
                val limitMinutes = strategy.dailyLimitMinutes.toLong()
                val alreadyNotified = rule.packageName in notified80Packages
                if (usageSessionEngine.shouldWarn(usedMinutes.toLong(), limitMinutes, alreadyNotified)) {
                    notified80Packages.add(rule.packageName)
                    val remaining = (limitMinutes - usedMinutes).coerceAtLeast(1)
                    postWarningNotification(rule.packageName, rule.label, remaining.toInt())
                }
            }
        }

        private fun postWarningNotification(
            packageName: String,
            label: String,
            remainingMinutes: Int,
        ) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Time Limit Warnings", NotificationManager.IMPORTANCE_DEFAULT),
            )
            val notification =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("$label — time limit warning")
                    .setContentText("${remainingMinutes}m remaining today")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            nm.notify(packageName.hashCode(), notification)
        }

        companion object {
            // Fallback window for the very first run only — see recordSessions()'s doc comment
            // for why every subsequent run windows from a persisted checkpoint instead.
            private const val WINDOW_MS = 15 * 60 * 1000L
            private const val WORK_NAME = "focusguard_usage_poller"
            private const val CHANNEL_ID = "focusguard_time_limit"
            private const val PREFS_NAME = "focusguard_usage_poller_state"
            private const val KEY_LAST_PROCESSED_TIMESTAMP = "last_processed_timestamp"

            // In-memory; cleared by MidnightResetWorker.doWork(). UsagePollerWorker and
            // MidnightResetWorker are separate WorkManager unique work items and can run
            // concurrently on different executor threads, so this needs a thread-safe set.
            val notified80Packages: MutableSet<String> = ConcurrentHashMap.newKeySet()

            fun schedule(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<UsagePollerWorker>(15, TimeUnit.MINUTES)
                        .addTag(WORK_NAME)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
