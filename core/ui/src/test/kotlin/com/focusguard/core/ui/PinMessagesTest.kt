package com.focusguard.core.ui

import com.focusguard.core.domain.model.PinAttemptResult
import com.focusguard.core.domain.model.PinVerificationToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class PinMessagesTest {
    @Test
    fun `incorrect PIN only mentions tries left when they're running out`() {
        assertEquals("Incorrect PIN", PinAttemptResult.Incorrect(4).errorMessage())
        assertEquals("Incorrect PIN · 2 tries left", PinAttemptResult.Incorrect(2).errorMessage())
        assertEquals("Incorrect PIN · 1 try left", PinAttemptResult.Incorrect(1).errorMessage())
    }

    @Test
    fun `lockout shows the wait rounded up`() {
        assertEquals("Too many tries. Try again in 30s", PinAttemptResult.LockedOut(30_000).errorMessage())
        assertEquals("Too many tries. Try again in 1s", PinAttemptResult.LockedOut(200).errorMessage())
        assertEquals("Too many tries. Try again in 2 min", PinAttemptResult.LockedOut(61_000).errorMessage())
        assertEquals("Too many tries. Try again in 15 min", PinAttemptResult.LockedOut(900_000).errorMessage())
    }

    @Test
    fun `success has no message`() {
        assertNull(PinAttemptResult.Success(PinVerificationToken(Instant.EPOCH)).errorMessage())
    }
}
