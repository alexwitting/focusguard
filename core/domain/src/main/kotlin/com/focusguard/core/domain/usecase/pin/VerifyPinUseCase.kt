package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import java.time.Instant

class VerifyPinUseCase(
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(rawPin: String): PinVerificationToken? {
        val isValid = pinRepository.verifyPin(rawPin)
        return if (isValid) PinVerificationToken(issuedAt = Instant.now()) else null
    }
}
