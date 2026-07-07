package com.focusguard.core.domain.model

import java.time.Duration
import java.time.Instant

data class PinVerificationToken(
    val issuedAt: Instant,
    val expiresAt: Instant = issuedAt.plus(Duration.ofMinutes(5)),
) {
    val isValid: Boolean get() = Instant.now().isBefore(expiresAt)
}
