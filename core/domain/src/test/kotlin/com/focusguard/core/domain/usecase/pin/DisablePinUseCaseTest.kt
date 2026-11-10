package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.time.TestClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class DisablePinUseCaseTest {
    private val clock = TestClock()
    private val repository = mockk<PinRepository>()
    private val useCase = DisablePinUseCase(repository, clock)

    @Test
    fun `disables PIN when token is valid`() =
        runTest {
            val token = PinVerificationToken(issuedAt = Instant.now())
            coEvery { repository.disablePin() } returns Unit

            useCase(token)

            coVerify(exactly = 1) { repository.disablePin() }
        }

    @Test
    fun `rejects an expired token and never disables PIN`() =
        runTest {
            val expiredToken =
                PinVerificationToken(
                    issuedAt = Instant.now().minus(Duration.ofMinutes(10)),
                    expiresAt = Instant.now().minus(Duration.ofMinutes(5)),
                )

            val result = runCatching { useCase(expiredToken) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { repository.disablePin() }
        }
}
