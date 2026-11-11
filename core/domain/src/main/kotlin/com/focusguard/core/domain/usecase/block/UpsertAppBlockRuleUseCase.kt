package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled
import com.focusguard.core.domain.time.ClockProvider

class UpsertAppBlockRuleUseCase(
    private val blockRuleRepository: BlockRuleRepository,
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        rule: AppBlockRule,
        token: PinVerificationToken?,
    ) {
        val windows = (rule.strategy as? BlockingStrategy.ScheduledBlock)?.windows.orEmpty()
        require(windows.none { it.isZeroLength }) { "Allowed hours need different start and end times" }
        pinRepository.requireValidTokenIfEnabled(token, "modify block rules", clockProvider)
        blockRuleRepository.upsertAppBlockRule(rule)
    }
}
