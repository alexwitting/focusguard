package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.model.PinAttemptResult
import com.focusguard.core.domain.model.PinLockout
import com.focusguard.core.domain.model.PinLockoutPolicy
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.time.ClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VerifyPinUseCaseTest {
    /** In-memory PinRepository; :core:domain can't use :core:testing's fakes. */
    private class InMemoryPins(
        private val pin: String,
    ) : PinRepository {
        var lockout = PinLockout()
        var verifyCalls = 0

        override fun isPinEnabled(): Flow<Boolean> = MutableStateFlow(true)

        override suspend fun setPin(rawPin: String) = Unit

        override suspend fun verifyPin(rawPin: String): Boolean {
            verifyCalls++
            return rawPin == pin
        }

        override suspend fun disablePin() = Unit

        override suspend fun getLockout() = lockout

        override suspend fun setLockout(lockout: PinLockout) {
            this.lockout = lockout
        }
    }

    private class ManualClock(
        var millis: Long,
    ) : ClockProvider {
        override fun today(): LocalDate = LocalDate.of(2026, 1, 1)

        override fun now(): LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)

        override fun epochMillis(): Long = millis
    }

    private val pins = InMemoryPins("123456")
    private val clock = ManualClock(1_000_000)
    private val useCase = VerifyPinUseCase(pins, clock)

    @Test
    fun `correct PIN returns a token issued at the clock's time`() =
        runTest {
            val result = useCase("123456")

            assertTrue(result is PinAttemptResult.Success)
            assertEquals(1_000_000L, (result as PinAttemptResult.Success).token.issuedAt.toEpochMilli())
        }

    @Test
    fun `wrong PINs count down the free attempts`() =
        runTest {
            assertEquals(PinAttemptResult.Incorrect(attemptsLeft = 4), useCase("000000"))
            assertEquals(PinAttemptResult.Incorrect(attemptsLeft = 3), useCase("000000"))
        }

    @Test
    fun `the fifth wrong PIN locks entry, and even the right PIN is refused until it passes`() =
        runTest {
            repeat(PinLockoutPolicy.FREE_ATTEMPTS - 1) { useCase("000000") }

            assertEquals(PinAttemptResult.LockedOut(30_000), useCase("000000"))

            clock.millis += 10_000
            val callsBefore = pins.verifyCalls
            assertEquals(PinAttemptResult.LockedOut(20_000), useCase("123456"))
            assertEquals(callsBefore, pins.verifyCalls, "PIN must not be checked while locked out")

            clock.millis += 20_000
            assertTrue(useCase("123456") is PinAttemptResult.Success)
            assertEquals(PinLockout(), pins.lockout)
        }

    @Test
    fun `lockouts grow with each further wrong PIN`() =
        runTest {
            val waits = mutableListOf<Long>()
            repeat(PinLockoutPolicy.FREE_ATTEMPTS + 5) {
                val result = useCase("000000")
                if (result is PinAttemptResult.LockedOut) {
                    waits += result.retryInMillis
                    clock.millis += result.retryInMillis
                }
            }
            assertEquals(listOf(30_000L, 60_000L, 120_000L, 300_000L, 900_000L, 900_000L), waits)
        }
}
