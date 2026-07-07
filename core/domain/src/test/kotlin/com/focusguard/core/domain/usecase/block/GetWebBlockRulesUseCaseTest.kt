package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetWebBlockRulesUseCaseTest {
    private val repository = mockk<WebBlockRuleRepository>()
    private val useCase = GetWebBlockRulesUseCase(repository)

    @Test
    fun `returns web block rules from the repository`() =
        runTest {
            val rules = listOf(WebBlockRule(domain = "example.com"))
            every { repository.getWebBlockRules() } returns flowOf(rules)

            val result = useCase().first()

            assertEquals(rules, result)
        }
}
