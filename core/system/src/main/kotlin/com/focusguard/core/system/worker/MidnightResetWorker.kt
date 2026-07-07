package com.focusguard.core.system.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class MidnightResetWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // UsageStatsManager resets automatically at midnight — nothing to clear.
            UsagePollerWorker.notified80Packages.clear()
            scheduleNext(applicationContext)
            return Result.success()
        }

        companion object {
            private const val WORK_NAME = "focusguard_midnight_reset"

            fun scheduleIfNeeded(context: Context) {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    buildRequest(),
                )
            }

            private fun scheduleNext(context: Context) {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    buildRequest(),
                )
            }

            private fun buildRequest(): androidx.work.OneTimeWorkRequest {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val delayMs =
                    java.time.Duration
                        .between(now, nextMidnight)
                        .toMillis()

                return OneTimeWorkRequestBuilder<MidnightResetWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag(WORK_NAME)
                    .build()
            }
        }
    }
