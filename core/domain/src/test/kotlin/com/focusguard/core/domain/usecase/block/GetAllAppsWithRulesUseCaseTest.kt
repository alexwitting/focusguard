package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetAllAppsWithRulesUseCaseTest {
    private val repository = mockk<BlockRuleRepository>()
    private val useCase = GetAllAppsWithRulesUseCase(repository)

    @Test
    fun `returns apps with rules from the repository`() =
        runTest {
            val apps = listOf(AppWithRule("com.app", "App", AppCategory.SOCIAL, rule = null))
            every { repository.getAllAppsWithRules() } returns flowOf(apps)

            val result = useCase().first()

            assertEquals(apps, result)
        }
}
