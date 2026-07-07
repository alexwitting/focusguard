package com.focusguard.core.testing

import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.repository.SystemHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSystemHealthRepository : SystemHealthRepository {
    private val _systemHealth =
        MutableStateFlow(
            SystemHealth(accessibilityServiceActive = false, vpnServiceActive = false),
        )
    private val _permissionsHealth =
        MutableStateFlow(
            PermissionsHealth(
                accessibilityEnabled = false,
                vpnPrepared = false,
                usageStatsGranted = false,
                batteryOptimizationIgnored = false,
                exactAlarmAllowed = false,
            ),
        )

    fun setSystemHealth(health: SystemHealth) {
        _systemHealth.value = health
    }

    fun setPermissionsHealth(health: PermissionsHealth) {
        _permissionsHealth.value = health
    }

    override fun observeSystemHealth(): Flow<SystemHealth> = _systemHealth

    override fun observePermissionsHealth(): Flow<PermissionsHealth> = _permissionsHealth

    override suspend fun checkSystemHealthNow(): SystemHealth = _systemHealth.value

    override suspend fun checkPermissionsNow(): PermissionsHealth = _permissionsHealth.value
}
