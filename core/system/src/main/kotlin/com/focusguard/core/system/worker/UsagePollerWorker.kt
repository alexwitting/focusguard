package com.focusguard.core.system.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.system.engine.UsageSessionEngine
import com.focusguard.core.system.receiver.AccessibilityWatchdog
import com.focusguard.core.system.usage.UsageRecorder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
        private val usageRecorder: UsageRecorder,
        private val accessibilityWatchdog: AccessibilityWatchdog,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                usageRecorder.recordUpToNow()
                checkTimeLimits()
                accessibilityWatchdog.check()
                Result.success()
            }

        private suspend fun checkTimeLimits() {
            val rules = blockRuleRepository.getAppBlockRules().first()

            for (rule in rules) {
                val strategy = rule.strategy
                if (!rule.enabled || strategy !is BlockingStrategy.TimeLimit) continue

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
                NotificationChannel(CHANNEL_ID, "Time limit warnings", NotificationManager.IMPORTANCE_DEFAULT),
            )
            val notification =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("$label")
                    .setContentText("$remainingMinutes min left of today's limit")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            nm.notify(packageName.hashCode(), notification)
        }

        companion object {
            private const val WORK_NAME = "focusguard_usage_poller"
            private const val CHANNEL_ID = "focusguard_time_limit"

            // Packages warned today; cleared at midnight. Thread-safe: the two workers can run
            // at the same time.
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
