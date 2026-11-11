package com.focusguard.core.system.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusguard.core.domain.model.UsageRetention
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
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
        private val usageRepository: UsageRepository,
        private val clockProvider: ClockProvider,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            UsagePollerWorker.notified80Packages.clear()
            // Logged rather than failed, so tonight's reschedule still happens.
            runCatching { usageRepository.pruneBefore(clockProvider.today().minusDays(UsageRetention.DAYS)) }
                .onFailure { Log.e(TAG, "Pruning old usage history failed", it) }
            scheduleNext(applicationContext)
            return Result.success()
        }

        companion object {
            private const val TAG = "MidnightResetWorker"
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
