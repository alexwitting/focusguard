package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetBedtimeUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<TimeWindow?> = repository.getBedtime()
}
