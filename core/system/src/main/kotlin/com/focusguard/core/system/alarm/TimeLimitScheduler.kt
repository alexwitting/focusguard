package com.focusguard.core.system.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusguard.core.domain.model.BlockReason
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeLimitScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        /** Blocks [packageName] for its daily time limit once [remainingMinutes] have passed. */
        fun scheduleBlock(
            packageName: String,
            label: String,
            remainingMinutes: Long,
        ) = scheduleBlockIn(packageName, label, remainingMinutes * MILLIS_PER_MINUTE, BlockReason.TIME_LIMIT_REACHED)

        /**
         * Blocks [packageName] for [reason] after [delayMillis] — a time limit running out, an
         * allowed-hours window closing, or bedtime starting while the app is open. One alarm per
         * package: scheduling again replaces it, so callers pass whichever block comes first.
         */
        fun scheduleBlockIn(
            packageName: String,
            label: String,
            delayMillis: Long,
            reason: BlockReason,
        ) {
            if (delayMillis <= 0) return
            cancelBlock(packageName)

            val pi = buildPendingIntent(packageName, label, reason, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            val triggerAt = System.currentTimeMillis() + delayMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        fun cancelBlock(packageName: String) {
            val pi = buildPendingIntent(packageName, label = "", reason = null, PendingIntent.FLAG_NO_CREATE) ?: return
            alarmManager.cancel(pi)
        }

        // Extras don't affect PendingIntent identity, so the FLAG_NO_CREATE lookup in cancelBlock
        // (no reason) still finds an alarm scheduled with one.
        private fun buildPendingIntent(
            packageName: String,
            label: String,
            reason: BlockReason?,
            flags: Int,
        ): PendingIntent? {
            val intent =
                Intent(context, TimeLimitAlarmReceiver::class.java).apply {
                    putExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_PACKAGE, packageName)
                    putExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_LABEL, label)
                    reason?.let { putExtra(TimeLimitAlarmReceiver.EXTRA_BLOCK_REASON, it.name) }
                }
            return PendingIntent.getBroadcast(
                context,
                packageName.hashCode(),
                intent,
                flags or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private companion object {
            const val MILLIS_PER_MINUTE = 60_000L
        }
    }
