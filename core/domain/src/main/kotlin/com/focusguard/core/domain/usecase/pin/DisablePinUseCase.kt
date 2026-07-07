package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository

class DisablePinUseCase(
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(token: PinVerificationToken) {
        require(token.isValid) { "PIN verification token has expired" }
        pinRepository.disablePin()
    }
}
