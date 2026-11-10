package com.focusguard

import app.cash.turbine.test
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.usecase.settings.RequestExtensionUseCase
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeUsageRepository
import com.focusguard.core.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class BlockedViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val usageRepository = FakeUsageRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val clockProvider = FakeClockProvider()

    private fun createViewModel(): BlockedViewModel =
        BlockedViewModel(
            usageRepository = usageRepository,
            settingsRepository = settingsRepository,
            requestExtensionUseCase = RequestExtensionUseCase(settingsRepository, clockProvider),
            clockProvider = clockProvider,
        )

    @BeforeEach
    fun setUp() {
        // Log.e isn't mocked under a plain JVM unit test (no Robolectric here) — only needed by
        // the failure tests below, but harmless to always stub.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `initialize always records a blocked attempt`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.initialize("com.app", BlockReason.ALWAYS_BLOCKED)

            assertEquals(1, usageRepository.getBlockedAttempts(LocalDate.now()).first())
        }

    @Test
    fun `initialize records a block only once, even when the screen recomposes or rotates`() =
        runTest {
            val viewModel = createViewModel()

            repeat(3) { viewModel.initialize("com.app", BlockReason.ALWAYS_BLOCKED) }

            assertEquals(1, usageRepository.getBlockedAttempts(LocalDate.now()).first())
        }

    @Test
    fun `a new block for a different app is recorded separately`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.initialize("com.app", BlockReason.ALWAYS_BLOCKED)
            viewModel.initialize("com.other", BlockReason.ALWAYS_BLOCKED)

            assertEquals(2, usageRepository.getBlockedAttempts(LocalDate.now()).first())
        }

    @Test
    fun `extension is offered when time limit reached and grace period enabled and not yet taken`() =
        runTest {
            settingsRepository.setGracePeriodEnabled(true)
            val viewModel = createViewModel()

            viewModel.initialize("com.app", BlockReason.TIME_LIMIT_REACHED)

            assertTrue(viewModel.state.value.canRequestExtension)
        }

    @Test
    fun `extension is not offered when grace period is disabled`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.initialize("com.app", BlockReason.TIME_LIMIT_REACHED)

            assertFalse(viewModel.state.value.canRequestExtension)
        }

    @Test
    fun `extension is not offered when already taken today`() =
        runTest {
            settingsRepository.setGracePeriodEnabled(true)
            settingsRepository.recordExtensionTaken("com.app", LocalDate.now())
            val viewModel = createViewModel()

            viewModel.initialize("com.app", BlockReason.TIME_LIMIT_REACHED)

            assertFalse(viewModel.state.value.canRequestExtension)
        }

    @Test
    fun `initialize failure is caught and logged instead of crashing`() =
        runTest {
            val failingUsageRepository =
                mockk<com.focusguard.core.domain.repository.UsageRepository> {
                    coEvery { recordBlockedAttempt(any()) } throws IllegalStateException("boom")
                }
            val viewModel =
                BlockedViewModel(
                    usageRepository = failingUsageRepository,
                    settingsRepository = settingsRepository,
                    requestExtensionUseCase = RequestExtensionUseCase(settingsRepository, clockProvider),
                    clockProvider = clockProvider,
                )

            viewModel.initialize("com.app", BlockReason.ALWAYS_BLOCKED)

            assertFalse(viewModel.state.value.canRequestExtension)
        }

    @Test
    fun `requestExtension records it, clears eligibility, and emits ExtensionGranted`() =
        runTest {
            settingsRepository.setGracePeriodEnabled(true)
            val viewModel = createViewModel()
            viewModel.initialize("com.app", BlockReason.TIME_LIMIT_REACHED)
            assertTrue(viewModel.state.value.canRequestExtension)

            viewModel.effects.test {
                viewModel.requestExtension("com.app")
                assertEquals(BlockedEffect.ExtensionGranted, awaitItem())
            }

            assertFalse(viewModel.state.value.canRequestExtension)
            assertTrue(settingsRepository.hasTakenExtensionToday("com.app", LocalDate.now()).first())
        }

    @Test
    fun `requestExtension failure is caught and logged instead of crashing`() =
        runTest {
            val failingUseCase = mockk<RequestExtensionUseCase>()
            coEvery { failingUseCase(any()) } throws IllegalStateException("boom")
            val viewModel =
                BlockedViewModel(
                    usageRepository = usageRepository,
                    settingsRepository = settingsRepository,
                    requestExtensionUseCase = failingUseCase,
                    clockProvider = clockProvider,
                )

            viewModel.effects.test {
                viewModel.requestExtension("com.app")
                expectNoEvents()
            }
        }
}
