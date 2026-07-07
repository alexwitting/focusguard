package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.repository.BlockRuleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetAppBlockRulesUseCaseTest {
    private val repository = mockk<BlockRuleRepository>()
    private val useCase = GetAppBlockRulesUseCase(repository)

    @Test
    fun `returns block rules from the repository`() =
        runTest {
            val rules = listOf(AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.AlwaysBlock))
            every { repository.getAppBlockRules() } returns flowOf(rules)

            val result = useCase().first()

            assertEquals(rules, result)
        }
}
