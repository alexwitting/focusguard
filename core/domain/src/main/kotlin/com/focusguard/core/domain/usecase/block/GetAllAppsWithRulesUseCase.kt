package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import kotlinx.coroutines.flow.Flow

class GetAllAppsWithRulesUseCase(
    private val repository: BlockRuleRepository,
) {
    operator fun invoke(): Flow<List<AppWithRule>> = repository.getAllAppsWithRules()
}
