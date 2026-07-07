package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import kotlinx.coroutines.flow.Flow

class GetWebBlockRulesUseCase(
    private val repository: WebBlockRuleRepository,
) {
    operator fun invoke(): Flow<List<WebBlockRule>> = repository.getWebBlockRules()
}
