package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.repository.PinRepository
import kotlinx.coroutines.flow.Flow

class GetPinEnabledUseCase(
    private val pinRepository: PinRepository,
) {
    operator fun invoke(): Flow<Boolean> = pinRepository.isPinEnabled()
}
