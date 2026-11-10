package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.time.TestClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class PinGateTest {
    private val issued = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = TestClock(issued)
    private val token = PinVerificationToken(issuedAt = issued)

    private fun pins(enabled: Boolean) = mockk<PinRepository> { every { isPinEnabled() } returns flowOf(enabled) }

    @Test
    fun `a token is accepted within its five minutes on the injected clock`() =
        runTest {
            clock.now = issued.plusSeconds(299)
            pins(enabled = true).requireValidTokenIfEnabled(token, "test", clock)
        }

    @Test
    fun `the same token is refused once the clock passes its expiry`() =
        runTest {
            clock.now = issued.plusSeconds(300)
            assertThrows<IllegalArgumentException> { pins(enabled = true).requireValidTokenIfEnabled(token, "test", clock) }
        }

    @Test
    fun `a missing token is refused when a PIN is set, and fine when it isn't`() =
        runTest {
            assertThrows<IllegalArgumentException> { pins(enabled = true).requireValidTokenIfEnabled(null, "test", clock) }
            pins(enabled = false).requireValidTokenIfEnabled(null, "test", clock)
        }
}
