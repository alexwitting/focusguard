package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.time.ClockProvider
import java.time.Instant

class DisablePinUseCase(
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(token: PinVerificationToken) {
        require(token.isValidAt(Instant.ofEpochMilli(clockProvider.epochMillis()))) { "PIN verification token has expired" }
        pinRepository.disablePin()
    }
}
