package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class GetDailyUsageUseCase(
    private val repository: UsageRepository,
) {
    operator fun invoke(date: LocalDate): Flow<List<AppUsageSummary>> = repository.getDailyUsage(date)
}
