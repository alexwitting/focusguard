package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Called by every use case that changes rules or settings. Throws unless the PIN is off or
 * [token] is still valid on [clock]. [action] completes "A valid PIN token is required to …".
 */
suspend fun PinRepository.requireValidTokenIfEnabled(
    token: PinVerificationToken?,
    action: String,
    clock: ClockProvider,
) {
    val pinEnabled = isPinEnabled().first()
    if (pinEnabled) {
        val now = Instant.ofEpochMilli(clock.epochMillis())
        require(token != null && token.isValidAt(now)) { "A valid PIN token is required to $action" }
    }
}
