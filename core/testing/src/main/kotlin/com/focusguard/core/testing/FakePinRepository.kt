package com.focusguard.core.testing

import com.focusguard.core.domain.repository.PinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePinRepository : PinRepository {
    private val _isPinEnabled = MutableStateFlow(false)
    private var storedPin: String? = null

    override fun isPinEnabled(): Flow<Boolean> = _isPinEnabled

    override suspend fun setPin(rawPin: String) {
        storedPin = rawPin
        _isPinEnabled.value = true
    }

    override suspend fun verifyPin(rawPin: String): Boolean = rawPin == storedPin

    override suspend fun disablePin() {
        storedPin = null
        _isPinEnabled.value = false
    }
}
