package com.focusguard.core.system.receiver

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceEnabled =
            am
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (serviceEnabled) {
            nm.cancel(NOTIFICATION_ID)
        } else {
            postAlert(context, nm)
        }
    }

    private fun postAlert(
        context: Context,
        nm: NotificationManager,
    ) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Service Alerts", NotificationManager.IMPORTANCE_HIGH),
        )
        val pi =
            PendingIntent.getActivity(
                context,
                0,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("FocusGuard blocking is disabled")
                .setContentText("Tap to re-enable the accessibility service and resume blocking")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "focusguard_watchdog"
        private const val NOTIFICATION_ID = 2001
    }
}
