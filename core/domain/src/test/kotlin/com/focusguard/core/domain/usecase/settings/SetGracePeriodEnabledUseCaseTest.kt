package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.time.TestClock
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

class SetGracePeriodEnabledUseCaseTest {
    private val clock = TestClock()
    private val settingsRepository = mockk<SettingsRepository>()
    private val pinRepository = mockk<PinRepository>()
    private val useCase = SetGracePeriodEnabledUseCase(settingsRepository, pinRepository, clock)

    @Test
    fun `sets the flag when PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { settingsRepository.setGracePeriodEnabled(true) } returns Unit

            useCase(true, token = null)

            coVerify(exactly = 1) { settingsRepository.setGracePeriodEnabled(true) }
        }

    @Test
    fun `sets the flag when PIN is enabled and token is valid`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)
            coEvery { settingsRepository.setGracePeriodEnabled(true) } returns Unit
            val token = PinVerificationToken(issuedAt = Instant.now())

            useCase(true, token)

            coVerify(exactly = 1) { settingsRepository.setGracePeriodEnabled(true) }
        }

    @Test
    fun `rejects the change when PIN is enabled and token is missing`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)

            val result = runCatching { useCase(true, token = null) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { settingsRepository.setGracePeriodEnabled(any()) }
        }
}
