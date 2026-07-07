package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.model.DailyTotal
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

class GetWeeklyTrendUseCaseTest {
    private val repository = mockk<UsageRepository>()
    private val today = LocalDate.of(2026, 1, 15)
    private val clockProvider = mockk<ClockProvider> { every { today() } returns today }
    private val useCase = GetWeeklyTrendUseCase(repository, clockProvider)

    @Test
    fun `returns weekly totals ending at the given date`() =
        runTest {
            val endDate = LocalDate.of(2026, 1, 20)
            val totals = listOf(DailyTotal(date = endDate, totalMinutes = 90))
            every { repository.getWeeklyTotals(endDate) } returns flowOf(totals)

            val result = useCase(endDate).first()

            assertEquals(totals, result)
        }

    @Test
    fun `defaults to today when no end date is provided`() =
        runTest {
            val totals = listOf(DailyTotal(date = today, totalMinutes = 15))
            every { repository.getWeeklyTotals(today) } returns flowOf(totals)

            val result = useCase().first()

            assertEquals(totals, result)
        }
}
