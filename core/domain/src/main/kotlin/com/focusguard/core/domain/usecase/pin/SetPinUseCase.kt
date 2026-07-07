package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.repository.PinRepository

class SetPinUseCase(
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(rawPin: String) {
        require(rawPin.length == 6 && rawPin.all { it.isDigit() }) {
            "PIN must be exactly 6 digits"
        }
        pinRepository.setPin(rawPin)
    }
}
