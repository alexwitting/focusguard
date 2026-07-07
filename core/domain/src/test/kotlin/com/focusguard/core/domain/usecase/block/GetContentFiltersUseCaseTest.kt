package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetContentFiltersUseCaseTest {
    private val repository = mockk<WebBlockRuleRepository>()
    private val useCase = GetContentFiltersUseCase(repository)

    @Test
    fun `returns content filters from the repository`() =
        runTest {
            val filters = setOf(ContentFilter.REELS, ContentFilter.SHORTS)
            every { repository.getContentFilters() } returns flowOf(filters)

            val result = useCase().first()

            assertEquals(filters, result)
        }
}
