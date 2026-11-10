package com.focusguard.feature.block

import app.cash.turbine.test
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.SystemHealth
import com.focusguard.core.domain.model.WebBlockRule
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
import com.focusguard.core.domain.usecase.settings.EnableWebContentBlockingUseCase
import com.focusguard.core.domain.usecase.settings.GetSystemHealthUseCase
import com.focusguard.core.domain.usecase.settings.GetWebContentBlockingEnabledUseCase
import com.focusguard.core.domain.usecase.usage.GetDailyUsageUseCase
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakePinRepository
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeSystemHealthRepository
import com.focusguard.core.testing.FakeUsageRepository
import com.focusguard.core.testing.FakeWebBlockRuleRepository
import com.focusguard.core.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
    private val settingsRepository = FakeSettingsRepository()
    private val systemHealthRepository = FakeSystemHealthRepository()

    @BeforeEach
    fun stubLog() {
        // The error paths log via android.util.Log, which throws under a plain JVM unit test.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }

    @AfterEach
    fun unstubLog() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createViewModel(
        getAllAppsOverride: GetAllAppsWithRulesUseCase? = null,
        upsertWebOverride: UpsertWebBlockRuleUseCase? = null,
    ): BlockViewModel =
        BlockViewModel(
            getAllAppsWithRules = getAllAppsOverride ?: GetAllAppsWithRulesUseCase(blockRuleRepository),
            getWebBlockRules = GetWebBlockRulesUseCase(webBlockRuleRepository),
            getContentFilters = GetContentFiltersUseCase(webBlockRuleRepository),
            upsertAppBlockRule = UpsertAppBlockRuleUseCase(blockRuleRepository, pinRepository, FakeClockProvider()),
            deleteAppBlockRule = DeleteAppBlockRuleUseCase(blockRuleRepository, pinRepository, FakeClockProvider()),
            upsertWebBlockRule = upsertWebOverride ?: UpsertWebBlockRuleUseCase(webBlockRuleRepository, pinRepository, FakeClockProvider()),
            deleteWebBlockRule = DeleteWebBlockRuleUseCase(webBlockRuleRepository, pinRepository, FakeClockProvider()),
            setContentFilter = SetContentFilterUseCase(webBlockRuleRepository, pinRepository, FakeClockProvider()),
            verifyPin = VerifyPinUseCase(pinRepository, FakeClockProvider()),
            getPinEnabled = GetPinEnabledUseCase(pinRepository),
            getDailyUsage = GetDailyUsageUseCase(usageRepository),
            clockProvider = clockProvider,
            getWebContentBlockingEnabled = GetWebContentBlockingEnabledUseCase(settingsRepository),
            enableWebContentBlocking = EnableWebContentBlockingUseCase(settingsRepository),
            getSystemHealth = GetSystemHealthUseCase(systemHealthRepository),
        )

    @Test
    fun `listed sites with no browser scan and no VPN are flagged as unprotected`() =
        runTest {
            systemHealthRepository.setSystemHealth(SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false))
            webBlockRuleRepository.upsertWebBlockRule(WebBlockRule(domain = "reddit.com", blockSubdomains = true))
            val viewModel = createViewModel()

            assertTrue(viewModel.state.value.websitesUnprotected)

            viewModel.onIntent(BlockIntent.EnableBrowserScan)

            assertTrue(settingsRepository.isWebContentBlockingEnabled().first())
            assertFalse(viewModel.state.value.websitesUnprotected)
        }

    @Test
    fun `a running VPN or an empty site list means no prompt`() =
        runTest {
            systemHealthRepository.setSystemHealth(SystemHealth(accessibilityServiceActive = true, vpnServiceActive = false))
            val viewModel = createViewModel()
            assertFalse(viewModel.state.value.websitesUnprotected) // nothing listed yet

            webBlockRuleRepository.upsertWebBlockRule(WebBlockRule(domain = "reddit.com", blockSubdomains = true))
            systemHealthRepository.setSystemHealth(SystemHealth(accessibilityServiceActive = true, vpnServiceActive = true))

            assertFalse(viewModel.state.value.websitesUnprotected)
        }

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

    @Test
    fun `AddWebRule saves only the hostname when a path or port is pasted in`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onIntent(BlockIntent.AddWebRule("reddit.com:443/r/all?sort=top", blockSubdomains = false))

            val saved = webBlockRuleRepository.getWebBlockRules().first().single()
            assertEquals("reddit.com", saved.domain)
        }

    @Test
    fun `a PIN shorter than six digits is rejected without being checked`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel()
            viewModel.onIntent(BlockIntent.RemoveWebRule("reddit.com"))

            viewModel.onIntent(BlockIntent.PinInputChanged("123"))
            viewModel.onIntent(BlockIntent.SubmitPin)

            assertEquals("Enter a 6-digit PIN", viewModel.state.value.pinError)
            assertTrue(viewModel.state.value.pinRequired)
        }

    @Test
    fun `a wrong PIN keeps the prompt open, clears the input and runs nothing`() =
        runTest {
            pinRepository.setPin("123456")
            webBlockRuleRepository.upsertWebBlockRule(WebBlockRule(domain = "reddit.com", blockSubdomains = true))
            val viewModel = createViewModel()
            viewModel.onIntent(BlockIntent.RemoveWebRule("reddit.com"))

            viewModel.onIntent(BlockIntent.PinInputChanged("000000"))
            viewModel.onIntent(BlockIntent.SubmitPin)

            assertEquals("Incorrect PIN", viewModel.state.value.pinError)
            assertEquals("", viewModel.state.value.pinInput)
            assertEquals(1, webBlockRuleRepository.getWebBlockRules().first().size)
        }

    private fun failingUpsert() = mockk<UpsertWebBlockRuleUseCase> { coEvery { this@mockk(any(), any()) } throws IllegalStateException("boom") }

    @Test
    fun `a change that fails shows a snackbar`() =
        runTest {
            val viewModel = createViewModel(upsertWebOverride = failingUpsert())

            viewModel.effects.test {
                viewModel.onIntent(BlockIntent.AddWebRule("reddit.com", blockSubdomains = true))
                assertEquals(BlockEffect.ShowSnackbar("Failed: boom"), awaitItem())
            }
        }

    @Test
    fun `a change that fails after the PIN is accepted shows a snackbar`() =
        runTest {
            pinRepository.setPin("123456")
            val viewModel = createViewModel(upsertWebOverride = failingUpsert())
            viewModel.onIntent(BlockIntent.AddWebRule("reddit.com", blockSubdomains = true))

            viewModel.effects.test {
                viewModel.onIntent(BlockIntent.PinInputChanged("123456"))
                viewModel.onIntent(BlockIntent.SubmitPin)
                assertEquals(BlockEffect.ShowSnackbar("Failed: boom"), awaitItem())
            }
            assertFalse(viewModel.state.value.pinRequired)
        }

    @Test
    fun `a failed load stops the spinner and says so instead of hanging`() =
        runTest {
            val failingApps = mockk<GetAllAppsWithRulesUseCase> { every { this@mockk() } returns flow { throw IllegalStateException("db") } }

            val viewModel = createViewModel(getAllAppsOverride = failingApps)

            assertFalse(viewModel.state.value.isLoading)
        }
}
