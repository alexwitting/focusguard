package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.model.AppUsageSummary
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

class GetWeeklyAppBreakdownUseCaseTest {
    private val repository = mockk<UsageRepository>()
    private val today = LocalDate.of(2026, 1, 15)
    private val clockProvider = mockk<ClockProvider> { every { today() } returns today }
    private val useCase = GetWeeklyAppBreakdownUseCase(repository, clockProvider)

    @Test
    fun `returns the weekly app breakdown ending at the given date`() =
        runTest {
            val endDate = LocalDate.of(2026, 1, 20)
            val breakdown =
                listOf(AppUsageSummary(packageName = "com.app", label = "App", totalMinutes = 90, limitMinutes = null))
            every { repository.getWeeklyAppBreakdown(endDate) } returns flowOf(breakdown)

            val result = useCase(endDate).first()

            assertEquals(breakdown, result)
        }

    @Test
    fun `defaults to today when no end date is provided`() =
        runTest {
            val breakdown =
                listOf(AppUsageSummary(packageName = "com.app", label = "App", totalMinutes = 15, limitMinutes = null))
            every { repository.getWeeklyAppBreakdown(today) } returns flowOf(breakdown)

            val result = useCase().first()

            assertEquals(breakdown, result)
        }
}
