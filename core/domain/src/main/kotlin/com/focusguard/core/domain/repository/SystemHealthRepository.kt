package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.SystemHealth
import kotlinx.coroutines.flow.Flow

interface SystemHealthRepository {
    fun observeSystemHealth(): Flow<SystemHealth>

    fun observePermissionsHealth(): Flow<PermissionsHealth>

    suspend fun checkSystemHealthNow(): SystemHealth

    suspend fun checkPermissionsNow(): PermissionsHealth
}
