package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled

class DeleteAppBlockRuleUseCase(
    private val blockRuleRepository: BlockRuleRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(
        packageName: String,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "delete block rules")
        blockRuleRepository.deleteAppBlockRule(packageName)
    }
}
