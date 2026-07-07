package com.focusguard.core.domain.usecase.pin

import com.focusguard.core.domain.repository.PinRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VerifyPinUseCaseTest {
    private val repository = mockk<PinRepository>()
    private val useCase = VerifyPinUseCase(repository)

    @Test
    fun `returns a valid token when PIN is correct`() =
        runTest {
            coEvery { repository.verifyPin("123456") } returns true

            val token = useCase("123456")

            assertNotNull(token)
            assertTrue(token!!.isValid)
        }

    @Test
    fun `returns null when PIN is incorrect`() =
        runTest {
            coEvery { repository.verifyPin("000000") } returns false

            val token = useCase("000000")

            assertNull(token)
        }
}
