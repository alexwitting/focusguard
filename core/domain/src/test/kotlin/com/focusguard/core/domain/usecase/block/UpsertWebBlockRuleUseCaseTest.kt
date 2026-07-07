package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.PinRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
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

class UpsertWebBlockRuleUseCaseTest {
    private val webBlockRuleRepository = mockk<WebBlockRuleRepository>()
    private val pinRepository = mockk<PinRepository>()
    private val useCase = UpsertWebBlockRuleUseCase(webBlockRuleRepository, pinRepository)

    private val rule = WebBlockRule(domain = "example.com")

    @Test
    fun `saves the rule when PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { webBlockRuleRepository.upsertWebBlockRule(rule) } returns Unit

            useCase(rule, token = null)

            coVerify(exactly = 1) { webBlockRuleRepository.upsertWebBlockRule(rule) }
        }

    @Test
    fun `saves the rule when PIN is enabled and token is valid`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)
            coEvery { webBlockRuleRepository.upsertWebBlockRule(rule) } returns Unit
            val token = PinVerificationToken(issuedAt = Instant.now())

            useCase(rule, token)

            coVerify(exactly = 1) { webBlockRuleRepository.upsertWebBlockRule(rule) }
        }

    @Test
    fun `rejects the save when PIN is enabled and token is missing`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)

            val result = runCatching { useCase(rule, token = null) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { webBlockRuleRepository.upsertWebBlockRule(any()) }
        }
}
