package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled

class SetGracePeriodEnabledUseCase(
    private val settingsRepository: SettingsRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(
        enabled: Boolean,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "change grace period setting")
        settingsRepository.setGracePeriodEnabled(enabled)
    }
}
