package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetWebContentBlockingEnabledUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.isWebContentBlockingEnabled()
}
