package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.repository.requireValidTokenIfEnabled
import com.focusguard.core.domain.time.ClockProvider

class SetContentFilterUseCase(
    private val webBlockRuleRepository: WebBlockRuleRepository,
    private val pinRepository: PinRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        filter: ContentFilter,
        enabled: Boolean,
        token: PinVerificationToken?,
    ) {
        pinRepository.requireValidTokenIfEnabled(token, "change content filters", clockProvider)
        webBlockRuleRepository.setContentFilter(filter, enabled)
    }
}
