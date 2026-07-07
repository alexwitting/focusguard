package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.repository.PinRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetPinUseCaseTest {
    private val repository = mockk<PinRepository>()
    private val useCase = SetPinUseCase(repository)

    @Test
    fun `accepts valid 6-digit PIN`() =
        runTest {
            coEvery { repository.setPin("123456") } returns Unit

            useCase("123456")

            coVerify(exactly = 1) { repository.setPin("123456") }
        }

    @Test
    fun `rejects PIN shorter than 6 digits`() =
        runTest {
            val result = runCatching { useCase("12345") }
            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { repository.setPin(any()) }
        }

    @Test
    fun `rejects PIN with non-digit characters`() =
        runTest {
            val result = runCatching { useCase("12345a") }
            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { repository.setPin(any()) }
        }

    @Test
    fun `rejects PIN longer than 6 digits`() =
        runTest {
            val result = runCatching { useCase("1234567") }
            assertTrue(result.isFailure)
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
            coVerify(exactly = 0) { repository.setPin(any()) }
        }
}
