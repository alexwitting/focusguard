package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SetBedtimeUseCaseTest {
    private val settingsRepository = mockk<SettingsRepository>()
    private val pinRepository = mockk<PinRepository>()
    private val useCase = SetBedtimeUseCase(settingsRepository, pinRepository)

    private val window = TimeWindow(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0)

    @Test
    fun `sets the bedtime when PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { settingsRepository.setBedtime(window) } returns Unit

            useCase(window, token = null)

            coVerify(exactly = 1) { settingsRepository.setBedtime(window) }
        }

    @Test
    fun `clears the bedtime when a null window is passed and PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { settingsRepository.setBedtime(null) } returns Unit

            useCase(null, token = null)

            coVerify(exactly = 1) { settingsRepository.setBedtime(null) }
        }

    @Test
    fun `sets the bedtime when PIN is enabled and token is valid`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)
            coEvery { settingsRepository.setBedtime(window) } returns Unit
            val token = PinVerificationToken(issuedAt = Instant.now())

            useCase(window, token)

            coVerify(exactly = 1) { settingsRepository.setBedtime(window) }
        }

    @Test
    fun `rejects the change when PIN is enabled and token is missing`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)

            val result = runCatching { useCase(window, token = null) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { settingsRepository.setBedtime(any()) }
        }
}
