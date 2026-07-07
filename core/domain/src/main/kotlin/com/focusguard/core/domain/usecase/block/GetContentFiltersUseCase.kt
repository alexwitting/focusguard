package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import kotlinx.coroutines.flow.Flow

class GetContentFiltersUseCase(
    private val repository: WebBlockRuleRepository,
) {
    operator fun invoke(): Flow<Set<ContentFilter>> = repository.getContentFilters()
}
