package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.PinVerificationToken
import kotlinx.coroutines.flow.first

/**
 * The PIN Gate Pattern: UseCases that mutate rules/settings call this before applying the
 * change. Throws if a PIN is enabled and [token] isn't a currently-valid verification token;
 * does nothing if PIN is disabled. [action] completes the message "A valid PIN token is
 * required to $action".
 */
suspend fun PinRepository.requireValidTokenIfEnabled(
    token: PinVerificationToken?,
    action: String,
) {
    val pinEnabled = isPinEnabled().first()
    if (pinEnabled) {
        require(token != null && token.isValid) { "A valid PIN token is required to $action" }
    }
}
