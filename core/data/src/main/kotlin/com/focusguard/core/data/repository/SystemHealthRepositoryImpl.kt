package com.focusguard.core.data.repository

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.repository.SystemHealthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SystemHealthRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SystemHealthRepository {
        // Each check is caught on its own so one failing system call can't end the polling loop.
        override fun observeSystemHealth(): Flow<SystemHealth> =
            flow {
                while (true) {
                    runCatching { checkSystemHealth() }
                        .onSuccess { emit(it) }
                        .onFailure { Log.e(TAG, "checkSystemHealth failed", it) }
                    delay(POLL_INTERVAL_MS)
                }
            }

        override fun observePermissionsHealth(): Flow<PermissionsHealth> =
            flow {
                while (true) {
                    runCatching { checkPermissionsHealth() }
                        .onSuccess { emit(it) }
                        .onFailure { Log.e(TAG, "checkPermissionsHealth failed", it) }
                    delay(POLL_INTERVAL_MS)
                }
            }

        override suspend fun checkSystemHealthNow(): SystemHealth = checkSystemHealth()

        override suspend fun checkPermissionsNow(): PermissionsHealth = checkPermissionsHealth()

        private fun checkSystemHealth(): SystemHealth {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val accessibilityActive =
                am
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == context.packageName }

            return SystemHealth(
                accessibilityServiceActive = accessibilityActive,
                vpnServiceActive = hasActiveVpnNetwork(),
            )
        }

        /**
         * Whether any current network is a VPN. A fresh snapshot each time, because a
         * NetworkCallback only reports changes after it's registered and would miss a tunnel that
         * was already up. The DNS VPN only routes one host, so it's never the active network.
         */
        @Suppress("DEPRECATION")
        private fun hasActiveVpnNetwork(): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }

        private fun checkPermissionsHealth(): PermissionsHealth {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val accessibilityEnabled =
                am
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == context.packageName }

            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val usageStatsGranted =
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED

            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)

            val exactAlarmAllowed =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                        .canScheduleExactAlarms()
                } else {
                    true
                }

            val vpnPrepared = VpnService.prepare(context) == null

            return PermissionsHealth(
                accessibilityEnabled = accessibilityEnabled,
                vpnPrepared = vpnPrepared,
                usageStatsGranted = usageStatsGranted,
                batteryOptimizationIgnored = batteryOptIgnored,
                exactAlarmAllowed = exactAlarmAllowed,
            )
        }

        companion object {
            private const val POLL_INTERVAL_MS = 30_000L
            private const val TAG = "SystemHealthRepository"
        }
    }
