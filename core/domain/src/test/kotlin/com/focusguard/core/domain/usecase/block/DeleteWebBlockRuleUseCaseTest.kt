package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
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

class DeleteWebBlockRuleUseCaseTest {
    private val clock = TestClock()
    private val webBlockRuleRepository = mockk<WebBlockRuleRepository>()
    private val pinRepository = mockk<PinRepository>()
    private val useCase = DeleteWebBlockRuleUseCase(webBlockRuleRepository, pinRepository, clock)

    @Test
    fun `deletes the rule when PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { webBlockRuleRepository.deleteWebBlockRule("example.com") } returns Unit

            useCase("example.com", token = null)

            coVerify(exactly = 1) { webBlockRuleRepository.deleteWebBlockRule("example.com") }
        }

    @Test
    fun `deletes the rule when PIN is enabled and token is valid`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)
            coEvery { webBlockRuleRepository.deleteWebBlockRule("example.com") } returns Unit
            val token = PinVerificationToken(issuedAt = Instant.now())

            useCase("example.com", token)

            coVerify(exactly = 1) { webBlockRuleRepository.deleteWebBlockRule("example.com") }
        }

    @Test
    fun `rejects deletion when PIN is enabled and token is missing`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)

            val result = runCatching { useCase("example.com", token = null) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { webBlockRuleRepository.deleteWebBlockRule(any()) }
        }
}
