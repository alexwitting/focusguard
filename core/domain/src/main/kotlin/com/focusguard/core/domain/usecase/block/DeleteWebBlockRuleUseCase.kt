package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled

class DeleteWebBlockRuleUseCase(
    private val webBlockRuleRepository: WebBlockRuleRepository,
    private val pinRepository: PinRepository,
) {
    suspend operator fun invoke(
        domain: String,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "delete web block rules")
        webBlockRuleRepository.deleteWebBlockRule(domain)
    }
}
