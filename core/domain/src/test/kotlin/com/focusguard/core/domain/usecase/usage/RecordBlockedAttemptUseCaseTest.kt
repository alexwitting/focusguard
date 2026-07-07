package com.focusguard.core.domain.usecase.usage

import com.focusguard.core.domain.repository.UsageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RecordBlockedAttemptUseCaseTest {
    private val repository = mockk<UsageRepository>()
    private val useCase = RecordBlockedAttemptUseCase(repository)

    @Test
    fun `records the blocked attempt for the given package`() =
        runTest {
            coEvery { repository.recordBlockedAttempt("com.app") } returns Unit

            useCase("com.app")

            coVerify(exactly = 1) { repository.recordBlockedAttempt("com.app") }
        }
}
