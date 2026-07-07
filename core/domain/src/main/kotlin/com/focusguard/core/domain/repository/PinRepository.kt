package com.focusguard.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface PinRepository {
    fun isPinEnabled(): Flow<Boolean>

    suspend fun setPin(rawPin: String)

    suspend fun verifyPin(rawPin: String): Boolean

    suspend fun disablePin()
}
