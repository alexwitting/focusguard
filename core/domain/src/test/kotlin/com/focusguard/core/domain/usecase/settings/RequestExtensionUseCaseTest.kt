package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.time.ClockProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RequestExtensionUseCaseTest {
    private val repository = mockk<SettingsRepository>()
    private val today = LocalDate.of(2026, 1, 15)
    private val clockProvider = mockk<ClockProvider> { every { today() } returns today }
    private val useCase = RequestExtensionUseCase(repository, clockProvider)

    @Test
    fun `returns false when grace period is disabled`() =
        runTest {
            every { repository.isGracePeriodEnabled() } returns flowOf(false)

            val result = useCase("com.example.app")

            assertFalse(result)
            coVerify(exactly = 0) { repository.recordExtensionTaken(any(), any()) }
        }

    @Test
    fun `returns false when extension already taken today`() =
        runTest {
            every { repository.isGracePeriodEnabled() } returns flowOf(true)
            every { repository.hasTakenExtensionToday(any(), any()) } returns flowOf(true)

            val result = useCase("com.example.app")

            assertFalse(result)
            coVerify(exactly = 0) { repository.recordExtensionTaken(any(), any()) }
        }

    @Test
    fun `records extension and returns true when eligible`() =
        runTest {
            val packageName = "com.example.app"
            every { repository.isGracePeriodEnabled() } returns flowOf(true)
            every { repository.hasTakenExtensionToday(packageName, any()) } returns flowOf(false)
            coEvery { repository.recordExtensionTaken(packageName, any()) } returns Unit

            val result = useCase(packageName)

            assertTrue(result)
            coVerify(exactly = 1) { repository.recordExtensionTaken(packageName, today) }
        }
}
