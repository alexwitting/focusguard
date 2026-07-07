package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import kotlinx.coroutines.flow.Flow

class GetAppBlockRulesUseCase(
    private val repository: BlockRuleRepository,
) {
    operator fun invoke(): Flow<List<AppBlockRule>> = repository.getAppBlockRules()
}
