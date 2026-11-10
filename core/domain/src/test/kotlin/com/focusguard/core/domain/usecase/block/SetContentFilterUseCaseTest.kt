package com.focusguard.core.domain.usecase.block

import com.focusguard.core.domain.model.ContentFilter
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

class SetContentFilterUseCaseTest {
    private val clock = TestClock()
    private val webBlockRuleRepository = mockk<WebBlockRuleRepository>()
    private val pinRepository = mockk<PinRepository>()
    private val useCase = SetContentFilterUseCase(webBlockRuleRepository, pinRepository, clock)

    @Test
    fun `sets the filter when PIN is disabled`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(false)
            coEvery { webBlockRuleRepository.setContentFilter(ContentFilter.REELS, true) } returns Unit

            useCase(ContentFilter.REELS, enabled = true, token = null)

            coVerify(exactly = 1) { webBlockRuleRepository.setContentFilter(ContentFilter.REELS, true) }
        }

    @Test
    fun `sets the filter when PIN is enabled and token is valid`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)
            coEvery { webBlockRuleRepository.setContentFilter(ContentFilter.REELS, true) } returns Unit
            val token = PinVerificationToken(issuedAt = Instant.now())

            useCase(ContentFilter.REELS, enabled = true, token)

            coVerify(exactly = 1) { webBlockRuleRepository.setContentFilter(ContentFilter.REELS, true) }
        }

    @Test
    fun `rejects the change when PIN is enabled and token is missing`() =
        runTest {
            every { pinRepository.isPinEnabled() } returns flowOf(true)

            val result = runCatching { useCase(ContentFilter.REELS, enabled = true, token = null) }

            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { webBlockRuleRepository.setContentFilter(any(), any()) }
        }
}
