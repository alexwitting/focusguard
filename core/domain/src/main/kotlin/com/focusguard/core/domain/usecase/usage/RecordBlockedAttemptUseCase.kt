package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.repository.UsageRepository

class RecordBlockedAttemptUseCase(
    private val repository: UsageRepository,
) {
    suspend operator fun invoke(packageName: String) {
        repository.recordBlockedAttempt(packageName)
    }
}
