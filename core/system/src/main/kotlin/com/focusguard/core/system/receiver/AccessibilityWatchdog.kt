package com.focusguard.core.system.receiver

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import com.focusguard.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Shows an ongoing "blocking is off" notification while the accessibility service is off, and
 * clears it once it's back. Runs from UsagePollerWorker; skipped until onboarding is done.
 */
class AccessibilityWatchdog
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend fun check() {
            if (!settingsRepository.isOnboardingComplete().first()) return
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val serviceEnabled =
                am
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (serviceEnabled) {
                nm.cancel(NOTIFICATION_ID)
            } else {
                postAlert(nm)
            }
        }

        private fun postAlert(nm: NotificationManager) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Blocking alerts", NotificationManager.IMPORTANCE_HIGH),
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
                    .setContentTitle("FocusGuard blocking is off")
                    .setContentText("Tap to turn the accessibility service back on")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build()
            nm.notify(NOTIFICATION_ID, notification)
        }

        private companion object {
            const val CHANNEL_ID = "focusguard_watchdog"
            const val NOTIFICATION_ID = 2001
        }
    }
