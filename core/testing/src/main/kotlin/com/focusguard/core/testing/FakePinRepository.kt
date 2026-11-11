package com.focusguard.core.testing

import com.focusguard.core.domain.model.PinLockout
import com.focusguard.core.domain.repository.PinRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePinRepository : PinRepository {
    private val _isPinEnabled = MutableStateFlow(false)
    private var storedPin: String? = null

    /** When true, [verifyPin] throws, like a failed read of the stored hash. */
    var failVerify = false

    /** When set, [verifyPin] waits for it, like the real slow hash. */
    var verifyGate: CompletableDeferred<Unit>? = null

    override fun isPinEnabled(): Flow<Boolean> = _isPinEnabled

    override suspend fun setPin(rawPin: String) {
        storedPin = rawPin
        _isPinEnabled.value = true
    }

    override suspend fun verifyPin(rawPin: String): Boolean {
        verifyGate?.await()
        check(!failVerify) { "PIN storage unavailable" }
        return rawPin == storedPin
    }

    override suspend fun disablePin() {
        storedPin = null
        lockout = PinLockout()
        _isPinEnabled.value = false
    }

    private var lockout = PinLockout()

    override suspend fun getLockout(): PinLockout = lockout

    override suspend fun setLockout(lockout: PinLockout) {
        this.lockout = lockout
    }
}
