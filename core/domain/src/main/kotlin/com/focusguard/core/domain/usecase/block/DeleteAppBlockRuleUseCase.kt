package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled
import com.focusguard.core.domain.time.ClockProvider

class DeleteAppBlockRuleUseCase(
    private val blockRuleRepository: BlockRuleRepository,
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        packageName: String,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "delete block rules", clockProvider)
        blockRuleRepository.deleteAppBlockRule(packageName)
    }
}
