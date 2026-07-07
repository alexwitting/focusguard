package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled

class UpsertAppBlockRuleUseCase(
    private val blockRuleRepository: BlockRuleRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(
        rule: AppBlockRule,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "modify block rules")
        blockRuleRepository.upsertAppBlockRule(rule)
    }
}
