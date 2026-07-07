package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class GetBlockedAttemptsUseCase(
    private val repository: UsageRepository,
    private val clockProvider: ClockProvider,
) {
    operator fun invoke(date: LocalDate = clockProvider.today()): Flow<Int> = repository.getBlockedAttempts(date)
}
