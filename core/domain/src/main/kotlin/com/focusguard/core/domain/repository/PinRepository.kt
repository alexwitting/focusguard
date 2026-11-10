package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.PinLockout
import kotlinx.coroutines.flow.Flow

interface PinRepository {
    fun isPinEnabled(): Flow<Boolean>

    suspend fun setPin(rawPin: String)

    suspend fun verifyPin(rawPin: String): Boolean

    suspend fun disablePin()

    /** Wrong-attempt state for [com.focusguard.core.domain.model.PinLockoutPolicy]. */
    suspend fun getLockout(): PinLockout

    suspend fun setLockout(lockout: PinLockout)
}
