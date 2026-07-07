package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PinVerificationTokenTest {
    @Test
    fun `token is valid when issued now`() {
        val token = PinVerificationToken(issuedAt = Instant.now())
        assertTrue(token.isValid)
    }

    @Test
    fun `token is invalid when expiry is in the past`() {
        val pastInstant = Instant.now().minusSeconds(400)
        val token =
            PinVerificationToken(
                issuedAt = pastInstant,
                expiresAt = pastInstant.plusSeconds(1),
            )
        assertFalse(token.isValid)
    }

    @Test
    fun `default expiry is 5 minutes after issue`() {
        val issuedAt = Instant.now()
        val token = PinVerificationToken(issuedAt = issuedAt)
        val expectedExpiry = issuedAt.plusSeconds(300)
        assertTrue(token.expiresAt >= expectedExpiry)
        assertTrue(token.expiresAt <= expectedExpiry.plusSeconds(2))
    }
}
