package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled
import com.focusguard.core.domain.time.ClockProvider

class UpsertWebBlockRuleUseCase(
    private val webBlockRuleRepository: WebBlockRuleRepository,
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        rule: WebBlockRule,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "modify web block rules", clockProvider)
        webBlockRuleRepository.upsertWebBlockRule(rule)
    }
}
