package com.focusguard.feature.block

import app.cash.turbine.test
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.usecase.block.DeleteAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.DeleteWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.GetAllAppsWithRulesUseCase
import com.focusguard.core.domain.usecase.block.GetContentFiltersUseCase
import com.focusguard.core.domain.usecase.block.GetWebBlockRulesUseCase
import com.focusguard.core.domain.usecase.block.SetContentFilterUseCase
import com.focusguard.core.domain.usecase.block.UpsertAppBlockRuleUseCase
import com.focusguard.core.domain.usecase.block.UpsertWebBlockRuleUseCase
import com.focusguard.core.domain.usecase.pin.GetPinEnabledUseCase
import com.focusguard.core.domain.usecase.pin.VerifyPinUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakePinRepository
import com.focusguard.core.testing.FakeUsageRepository
import com.focusguard.core.testing.FakeWebBlockRuleRepository
import com.focusguard.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Sociable tests: real [BlockViewModel] + real UseCases wired to the `Fake*Repository`
 * doubles from `:core:testing`, exercising the PIN-gate dance end to end.
 */
class BlockViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val blockRuleRepository = FakeBlockRuleRepository()
    private val webBlockRuleRepository = FakeWebBlockRuleRepository()
    private val pinRepository = FakePinRepository()
    private val usageRepository = FakeUsageRepository()
    private val clockProvider = FakeClockProvider()

    private fun createViewModel(): BlockViewModel =
        BlockViewModel(
            getAllAppsWithRules = GetAllAppsWithRulesUseCase(blockRuleRepository),
            getWebBlockRules = GetWebBlockRulesUseCase(webBlockRuleRepository),
            getContentFilters = GetContentFiltersUseCase(webBlockRuleRepository),
            upsertAppBlockRule = UpsertAppBlockRuleUseCase(blockRuleRepository, pinRepository),
            deleteAppBlockRule = DeleteAppBlockRuleUseCase(blockRuleRepository, pinRepository),
            upsertWebBlockRule = UpsertWebBlockRuleUseCase(webBlockRuleRepository, pinRepository),
            deleteWebBlockRule = DeleteWebBlockRuleUseCase(webBlockRuleRepository, pinRepository),
            setContentFilter = SetContentFilterUseCase(webBlockRuleRepository, pinRepository),
            verifyPin = VerifyPinUseCase(pinRepository),
            getPinEnabled = GetPinEnabledUseCase(pinRepository),
            getDailyUsage = GetDailyUsageUseCase(usageRepository),
            clockProvider = clockProvider,
        )

    @Test
    fun `mutation runs immediately when PIN is disabled`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(BlockIntent.SetAppStrategy("com.app", "App", BlockingStrategy.AlwaysBlock))

            assertFalse(viewModel.state.value.pinRequired)
            assertEquals(
                BlockingStrategy.AlwaysBlock,
                blockRuleRepository.getAppBlockRule("com.app").first()?.strategy,
            )
        }

    @Test
    fun `mutation is deferred behind the PIN dialog when PIN is enabled`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()

            viewModel.onIntent(BlockIntent.SetAppStrategy("com.app", "App", BlockingStrategy.AlwaysBlock))

            assertTrue(viewModel.state.value.pinRequired)
            assertNull(blockRuleRepository.getAppBlockRule("com.app").first())
        }

    @Test
    fun `wrong PIN shows an error and keeps the dialog open without running the operation`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(BlockIntent.SetAppStrategy("com.app", "App", BlockingStrategy.AlwaysBlock))

            viewModel.onIntent(BlockIntent.PinInputChanged("000000"))
            viewModel.onIntent(BlockIntent.SubmitPin)

            assertEquals("Incorrect PIN", viewModel.state.value.pinError)
            assertTrue(viewModel.state.value.pinRequired)
            assertNull(blockRuleRepository.getAppBlockRule("com.app").first())
        }

    @Test
    fun `correct PIN runs the deferred operation exactly once and closes the dialog`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(BlockIntent.SetAppStrategy("com.app", "App", BlockingStrategy.AlwaysBlock))

            viewModel.onIntent(BlockIntent.PinInputChanged("123456"))
            viewModel.onIntent(BlockIntent.SubmitPin)

            assertFalse(viewModel.state.value.pinRequired)
            assertEquals(
                BlockingStrategy.AlwaysBlock,
                blockRuleRepository.getAppBlockRule("com.app").first()?.strategy,
            )
        }

    @Test
    fun `dismissing the PIN dialog discards the pending operation`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(BlockIntent.SetAppStrategy("com.app", "App", BlockingStrategy.AlwaysBlock))

            viewModel.onIntent(BlockIntent.DismissPinDialog)

            assertFalse(viewModel.state.value.pinRequired)
            assertNull(blockRuleRepository.getAppBlockRule("com.app").first())
        }

    @Test
    fun `AddWebRule rejects a domain without a dot before PIN gating ever runs`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.effects.test {
                viewModel.onIntent(BlockIntent.AddWebRule("notadomain", blockSubdomains = true))
                assertEquals(
                    BlockEffect.ShowSnackbar("Enter a valid domain (e.g. instagram.com)"),
                    awaitItem(),
                )
            }
            assertTrue(webBlockRuleRepository.getWebBlockRules().first().isEmpty())
        }

    @Test
    fun `AddWebRule strips scheme and www before saving`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(BlockIntent.AddWebRule("https://www.Instagram.com/", blockSubdomains = true))

            val saved = webBlockRuleRepository.getWebBlockRules().first().single()
            assertEquals("instagram.com", saved.domain)
        }
}
