package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.model.PinAttemptResult
import com.focusguard.core.domain.model.PinLockout
import com.focusguard.core.domain.model.PinLockoutPolicy
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.time.ClockProvider
import java.time.Instant

/**
 * Checks a PIN, applying [PinLockoutPolicy]: while locked out the PIN isn't even compared, a
 * correct PIN clears the wrong-attempt count, and each wrong one adds to it.
 */
class VerifyPinUseCase(
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(rawPin: String): PinAttemptResult {
        val now = clockProvider.epochMillis()
        val lockout = pinRepository.getLockout()
        if (now < lockout.lockedUntilEpochMillis) {
            return PinAttemptResult.LockedOut(lockout.lockedUntilEpochMillis - now)
        }

        if (pinRepository.verifyPin(rawPin)) {
            if (lockout != PinLockout()) pinRepository.setLockout(PinLockout())
            return PinAttemptResult.Success(PinVerificationToken(issuedAt = Instant.ofEpochMilli(now)))
        }

        val failures = lockout.failedAttempts + 1
        val lockMillis = PinLockoutPolicy.lockoutMillis(failures)
        pinRepository.setLockout(PinLockout(failures, if (lockMillis > 0) now + lockMillis else 0))
        return if (lockMillis > 0) {
            PinAttemptResult.LockedOut(lockMillis)
        } else {
            PinAttemptResult.Incorrect(attemptsLeft = PinLockoutPolicy.FREE_ATTEMPTS - failures)
        }
    }
}
