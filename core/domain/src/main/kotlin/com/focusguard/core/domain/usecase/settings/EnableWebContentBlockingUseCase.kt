package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.repository.SettingsRepository

class EnableWebContentBlockingUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke() {
        repository.enableWebContentBlocking()
    }
}
