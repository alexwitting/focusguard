package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.first

class RequestExtensionUseCase(
    private val repository: SettingsRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(packageName: String): Boolean {
        val gracePeriodEnabled = repository.isGracePeriodEnabled().first()
        if (!gracePeriodEnabled) return false

        val today = clockProvider.today()
        val alreadyTaken = repository.hasTakenExtensionToday(packageName, today).first()
        if (alreadyTaken) return false

        repository.recordExtensionTaken(packageName, today)
        return true
    }
}
