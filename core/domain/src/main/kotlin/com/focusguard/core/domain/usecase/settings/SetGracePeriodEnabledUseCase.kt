package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled
import com.focusguard.core.domain.time.ClockProvider

class SetGracePeriodEnabledUseCase(
    private val settingsRepository: SettingsRepository,
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        enabled: Boolean,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "change grace period setting", clockProvider)
        settingsRepository.setGracePeriodEnabled(enabled)
    }
}
