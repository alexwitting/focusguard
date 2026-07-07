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
        // A raw `while (true) { emit(check()) }` would let one transient failure (any of the
        // system services above throwing, e.g. a flaky OEM AccessibilityManager/ConnectivityManager
        // call) propagate out of the flow builder and permanently end the polling loop for the
        // rest of the process's lifetime — collectors would silently stop receiving updates
        // forever, not just miss one tick. Catching per-iteration keeps the poll alive so a
        // one-off failure self-heals on the next tick instead of freezing health status forever.
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
         * Scans every current network's capabilities for TRANSPORT_VPN, checked fresh on every
         * 30s poll and on-demand refresh — deliberately a synchronous snapshot, not an
         * event-driven NetworkCallback. This repository is a process-lifetime Hilt singleton, and
         * DnsVpnService's tunnel is a separate foreground service that can outlive the main
         * process across an OS-triggered restart; a callback only reports transitions seen
         * *after* it's registered, so it would report permanently inactive if the tunnel was
         * already up before the singleton was (re-)created. A snapshot has no such blind spot.
         * `allNetworks` is deprecated in favor of callback-based tracking, but a callback can't
         * answer "is a VPN active right now" for a tunnel that predates it — so the deprecated,
         * synchronous API is the correct tool here, not an oversight.
         * `activeNetwork` alone can't substitute for the scan either: DnsVpnService only claims a
         * narrow single-host route (see DnsVpnService.kt), not a full-tunnel default route, so it
         * never becomes the OS's "active" network.
         */
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
