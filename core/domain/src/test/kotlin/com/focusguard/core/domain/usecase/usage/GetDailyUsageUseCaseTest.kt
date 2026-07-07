package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.repository.UsageRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GetDailyUsageUseCaseTest {
    private val repository = mockk<UsageRepository>()
    private val useCase = GetDailyUsageUseCase(repository)

    @Test
    fun `returns daily usage for the given date`() =
        runTest {
            val date = LocalDate.of(2026, 1, 15)
            val summaries = listOf(AppUsageSummary(packageName = "com.app", label = "App", totalMinutes = 42, limitMinutes = 60))
            every { repository.getDailyUsage(date) } returns flowOf(summaries)

            val result = useCase(date).first()

            assertEquals(summaries, result)
        }
}
