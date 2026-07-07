package com.focusguard.core.system.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

        fun scheduleBlock(
            packageName: String,
            label: String,
            remainingMinutes: Long,
        ) {
            if (remainingMinutes <= 0) return
            cancelBlock(packageName)

            val pi = buildPendingIntent(packageName, label, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            val triggerAt = System.currentTimeMillis() + remainingMinutes * 60_000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        fun cancelBlock(packageName: String) {
            val pi = buildPendingIntent(packageName, label = "", PendingIntent.FLAG_NO_CREATE) ?: return
            alarmManager.cancel(pi)
        }

        private fun buildPendingIntent(
            packageName: String,
            label: String,
            flags: Int,
        ): PendingIntent? {
            val intent =
                Intent(context, TimeLimitAlarmReceiver::class.java).apply {
                    putExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_PACKAGE, packageName)
                    putExtra(TimeLimitAlarmReceiver.EXTRA_BLOCKED_LABEL, label)
                }
            return PendingIntent.getBroadcast(
                context,
                packageName.hashCode(),
                intent,
                flags or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
