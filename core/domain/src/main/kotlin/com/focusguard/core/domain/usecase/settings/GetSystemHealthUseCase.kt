package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PermissionsHealth
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.repository.SystemHealthRepository
import kotlinx.coroutines.flow.Flow

class GetSystemHealthUseCase(
    private val repository: SystemHealthRepository,
) {
    fun observeSystemHealth(): Flow<SystemHealth> = repository.observeSystemHealth()

    fun observePermissionsHealth(): Flow<PermissionsHealth> = repository.observePermissionsHealth()

    suspend fun checkSystemHealthNow(): SystemHealth = repository.checkSystemHealthNow()

    suspend fun checkPermissionsNow(): PermissionsHealth = repository.checkPermissionsNow()
}
