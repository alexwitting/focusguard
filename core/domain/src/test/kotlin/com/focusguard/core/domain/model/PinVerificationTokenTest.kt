package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PinVerificationTokenTest {
    @Test
    fun `token is valid until five minutes after it was issued, and not after`() {
        val issued = Instant.parse("2026-01-01T12:00:00Z")
        val token = PinVerificationToken(issuedAt = issued)
        assertTrue(token.isValidAt(issued))
        assertTrue(token.isValidAt(issued.plusSeconds(299)))
        assertFalse(token.isValidAt(issued.plusSeconds(300)))
    }

    @Test
    fun `token is invalid when expiry is in the past`() {
        val pastInstant = Instant.now().minusSeconds(400)
        val token =
            PinVerificationToken(
                issuedAt = pastInstant,
                expiresAt = pastInstant.plusSeconds(1),
            )
        assertFalse(token.isValidAt(Instant.now()))
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
