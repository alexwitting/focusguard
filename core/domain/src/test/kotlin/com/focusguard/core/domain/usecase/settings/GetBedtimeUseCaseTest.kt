package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek

class GetBedtimeUseCaseTest {
    private val repository = mockk<SettingsRepository>()
    private val useCase = GetBedtimeUseCase(repository)

    @Test
    fun `returns the bedtime window from the repository`() =
        runTest {
            val window = TimeWindow(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0, days = setOf(DayOfWeek.MONDAY))
            every { repository.getBedtime() } returns flowOf(window)

            val result = useCase().first()

            assertEquals(window, result)
        }

    @Test
    fun `returns null when no bedtime is configured`() =
        runTest {
            every { repository.getBedtime() } returns flowOf(null)

            val result = useCase().first()

            assertEquals(null, result)
        }
}
