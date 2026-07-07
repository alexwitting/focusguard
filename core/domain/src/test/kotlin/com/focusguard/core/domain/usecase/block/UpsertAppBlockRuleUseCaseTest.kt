package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.PinVerificationToken
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.PinRepository
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

class UpsertAppBlockRuleUseCaseTest {
    private val blockRuleRepository = mockk<BlockRuleRepository>()
    private val pinRepository = mockk<PinRepository>()
    private val useCase = UpsertAppBlockRuleUseCase(blockRuleRepository, pinRepository)

    private val rule = AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.AlwaysBlock)

    @Test
    fun `saves the rule when PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { blockRuleRepository.upsertAppBlockRule(rule) } returns Unit

            useCase(rule, token = null)

            coVerify(exactly = 1) { blockRuleRepository.upsertAppBlockRule(rule) }
        }

    @Test
    fun `saves the rule when PIN is enabled and token is valid`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)
            coEvery { blockRuleRepository.upsertAppBlockRule(rule) } returns Unit
            val token = PinVerificationToken(issuedAt = Instant.now())

            useCase(rule, token)

            coVerify(exactly = 1) { blockRuleRepository.upsertAppBlockRule(rule) }
        }

    @Test
    fun `rejects the save when PIN is enabled and token is missing`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)

            val result = runCatching { useCase(rule, token = null) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { blockRuleRepository.upsertAppBlockRule(any()) }
        }
}
