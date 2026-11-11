package com.focusguard.core.domain.model

import java.time.Duration
import java.time.Instant

data class PinVerificationToken(
    val issuedAt: Instant,
    val expiresAt: Instant = issuedAt.plus(Duration.ofMinutes(5)),
) {
    /** Whether the token still authorises a change at [now]. */
    fun isValidAt(now: Instant): Boolean = now.isBefore(expiresAt)
}
