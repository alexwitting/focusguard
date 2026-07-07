package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.time.ClockProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GetBlockedAttemptsUseCaseTest {
    private val repository = mockk<UsageRepository>()
    private val today = LocalDate.of(2026, 1, 15)
    private val clockProvider = mockk<ClockProvider> { every { today() } returns today }
    private val useCase = GetBlockedAttemptsUseCase(repository, clockProvider)

    @Test
    fun `returns blocked attempts for the given date`() =
        runTest {
            val date = LocalDate.of(2026, 1, 20)
            every { repository.getBlockedAttempts(date) } returns flowOf(3)

            val result = useCase(date).first()

            assertEquals(3, result)
        }

    @Test
    fun `defaults to today when no date is provided`() =
        runTest {
            every { repository.getBlockedAttempts(today) } returns flowOf(5)

            val result = useCase().first()

            assertEquals(5, result)
        }
}
