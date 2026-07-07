package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class GetWeeklyTrendUseCase(
    private val repository: UsageRepository,
    private val clockProvider: ClockProvider,
) {
    operator fun invoke(endDate: LocalDate = clockProvider.today()): Flow<List<DailyTotal>> = repository.getWeeklyTotals(endDate)
}
