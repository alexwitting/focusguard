package com.focusguard.core.domain.usecase.settings

import com.focusguard.core.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetGracePeriodEnabledUseCaseTest {
    private val repository = mockk<SettingsRepository>()
    private val useCase = GetGracePeriodEnabledUseCase(repository)

    @Test
    fun `returns the grace period flag from the repository`() =
        runTest {
            every { repository.isGracePeriodEnabled() } returns flowOf(true)

            val result = useCase().first()

            assertTrue(result)
        }
}
