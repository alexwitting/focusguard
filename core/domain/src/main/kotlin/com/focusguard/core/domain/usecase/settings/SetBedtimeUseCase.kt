package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled
import com.focusguard.core.domain.time.ClockProvider

class SetBedtimeUseCase(
    private val settingsRepository: SettingsRepository,
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        window: TimeWindow?,
        token: PinVerificationToken?,
    ) {
        require(window?.isZeroLength != true) { "Bedtime needs different start and end times" }
        pinRepository.requireValidTokenIfEnabled(token, "change bedtime", clockProvider)
        settingsRepository.setBedtime(window)
    }
}
