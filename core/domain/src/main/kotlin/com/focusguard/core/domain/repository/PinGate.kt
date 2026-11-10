package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * The PIN Gate Pattern: UseCases that mutate rules/settings call this before applying the
 * change. Throws if a PIN is enabled and [token] isn't a currently-valid verification token;
 * does nothing if PIN is disabled. [action] completes the message "A valid PIN token is
 * required to $action".
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
