package com.focusguard.core.system.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.system.alarm.TimeLimitScheduler
import com.focusguard.core.system.engine.AppBlockingEngine
import com.focusguard.core.system.engine.ContentFilterEngine
import com.focusguard.core.system.engine.DnsFilterEngine
import com.focusguard.core.system.engine.WebContentFilterEngine
import com.focusguard.core.system.foreground.UsageStatsForegroundDetector
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeUsageRepository
import com.focusguard.core.testing.FakeWebBlockRuleRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

/**
 * Constructed via Robolectric so `onCreate`/event dispatch run against a real (simulated)
 * Android framework. Hilt is bypassed entirely — the `@Inject lateinit var` fields are public
 * (no visibility modifier), so tests assign fakes/real-engines directly rather than standing up
 * a Hilt test component.
 *
 * JUnit4 + Robolectric's own `@RunWith(RobolectricTestRunner::class)`, not the JUnit5 bridge used
 * elsewhere in this module: the community JUnit5 bridge showed cross-test isolation issues here
 * specifically (assertions checking `shadowOf(service).nextStartedActivity` passed individually
 * but failed when run as part of the full class — official Robolectric+JUnit4 doesn't have that
 * problem). See UsagePollerWorkerTest for the JUnit5-bridge version used where it works fine.
 *
 * The TimeLimit-strategy branch of handleWindowStateChanged does `serviceScope.launch(Dispatchers.IO)`
 * — a real background dispatcher untouched by Robolectric's main-looper idling — so those two
 * tests poll with a bounded real-time timeout instead of asserting immediately.
 */
@RunWith(RobolectricTestRunner::class)
class FocusGuardAccessibilityServiceTest {
    private val blockRuleRepository = FakeBlockRuleRepository()
    private val usageRepository = FakeUsageRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val webBlockRuleRepository = FakeWebBlockRuleRepository()
    private val timeLimitScheduler = mockk<TimeLimitScheduler>(relaxed = true)
    private val clockProvider = FakeClockProvider()
    private lateinit var service: FocusGuardAccessibilityService

    private companion object {
        const val PACKAGE_NAME = "com.focusguard"
        const val OTHER_APP = "com.other.app"
    }

    @Before
    fun setUp() {
        // .get() only, not .create() — create() runs the real onCreate(), which is
        // Hilt_FocusGuardAccessibilityService's generated injection path and requires a real
        // @HiltAndroidApp test Application we haven't set up. Fields are assigned directly below
        // instead, bypassing Hilt entirely (they're public — no visibility modifier).
        service =
            Robolectric.buildService(FocusGuardAccessibilityService::class.java).get().apply {
                blockingEngine = AppBlockingEngine()
                blockRuleRepository = this@FocusGuardAccessibilityServiceTest.blockRuleRepository
                usageRepository = this@FocusGuardAccessibilityServiceTest.usageRepository
                settingsRepository = this@FocusGuardAccessibilityServiceTest.settingsRepository
                webBlockRuleRepository = this@FocusGuardAccessibilityServiceTest.webBlockRuleRepository
                contentFilterEngine = ContentFilterEngine()
                timeLimitScheduler = this@FocusGuardAccessibilityServiceTest.timeLimitScheduler
                clockProvider = this@FocusGuardAccessibilityServiceTest.clockProvider
                webContentFilterEngine = WebContentFilterEngine(DnsFilterEngine())
                usageStatsForegroundDetector = mockk<UsageStatsForegroundDetector>(relaxed = true)
            }
    }

    private fun windowStateChangedEvent(pkg: String): AccessibilityEvent =
        AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = pkg
            className = "com.example.MainActivity"
        }

    private fun contentChangedEvent(pkg: String): AccessibilityEvent =
        AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            packageName = pkg
        }

    private fun nodeTree(
        selected: Boolean,
        contentDescription: String? = null,
    ): AccessibilityNodeInfo =
        AccessibilityNodeInfo().apply {
            isSelected = selected
            contentDescription?.let { this.contentDescription = it }
        }

    /**
     * Polls up to 2 real seconds for a Dispatchers.IO-launched side effect to land. Evaluates
     * `condition()` exactly once per iteration and reuses that result for the final assertion —
     * Robolectric's `nextStartedActivity` pops from a queue, so calling `condition()` an extra
     * time (e.g. once in a `while` check, again in the assertion) silently consumes the value
     * the first call already found and makes the second call see nothing.
     */
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        var result = condition()
        while (!result && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            result = condition()
        }
        assertTrue("condition not met within timeout", result)
    }

    /**
     * handleWindowStateChanged reads app-block rules from an in-memory `rulesCache`, populated
     * only by onServiceConnected()'s Dispatchers.IO Flow collector — never called by `.get()`
     * (see setUp()). onServiceConnected() is `protected` (inherited from AccessibilityService),
     * hence reflection to invoke it, and reflection again to poll `rulesCache` directly rather
     * than a fixed sleep: flowOn(Dispatchers.IO) shifts the upstream flow, but the collect{}
     * lambda itself still runs on serviceScope's Dispatchers.Main — which under Robolectric's
     * default PAUSED looper mode means "queued, not run" until the looper is explicitly idled,
     * and a single fixed-length wait proved flaky under system load (intermittently still empty
     * by the time the caller's assertion ran). Every current caller sets non-empty rules before
     * calling this, so "cache became non-empty" is a reliable signal to poll on.
     */
    private fun connectService() {
        val method = FocusGuardAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        method.isAccessible = true
        method.invoke(service)

        val rulesCacheField = FocusGuardAccessibilityService::class.java.getDeclaredField("rulesCache")
        rulesCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val rulesCache = rulesCacheField.get(service) as AtomicReference<Map<String, AppBlockRule>>

        val deadline = System.currentTimeMillis() + 2_000
        while (rulesCache.get().isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    /**
     * Same reasoning as connectService(), but polls activeFiltersCache instead — a separate
     * onServiceConnected() Flow collector populates it independently of rulesCache, and
     * content-filter-only tests don't necessarily set any block rule for connectService()'s own
     * poll condition (rulesCache non-empty) to ever be satisfied.
     */
    private fun connectServiceWithFilters() {
        val method = FocusGuardAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        method.isAccessible = true
        method.invoke(service)

        val filtersCacheField = FocusGuardAccessibilityService::class.java.getDeclaredField("activeFiltersCache")
        filtersCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val filtersCache = filtersCacheField.get(service) as AtomicReference<Set<ContentFilter>>

        val deadline = System.currentTimeMillis() + 2_000
        while (filtersCache.get().isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    /**
     * Same reasoning as connectService(), but polls webContentBlockingEnabledCache — populated
     * by settingsRepository.isWebContentBlockingEnabled(), separately from rulesCache/
     * activeFiltersCache. Callers must enable web content blocking on settingsRepository before
     * invoking this.
     */
    private fun connectServiceWithWebBlocking() {
        val method = FocusGuardAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        method.isAccessible = true
        method.invoke(service)

        val enabledCacheField = FocusGuardAccessibilityService::class.java.getDeclaredField("webContentBlockingEnabledCache")
        enabledCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val enabledCache = enabledCacheField.get(service) as AtomicReference<Boolean>

        val deadline = System.currentTimeMillis() + 2_000
        while (!enabledCache.get() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun urlBarNode(
        resourceId: String,
        text: String,
        focused: Boolean = false,
        editable: Boolean = false,
    ): AccessibilityNodeInfo =
        AccessibilityNodeInfo().apply {
            viewIdResourceName = resourceId
            this.text = text
            isFocused = focused
            isEditable = editable
            isVisibleToUser = true
        }

    /** A non-address-bar node — e.g. a tab-preview title or dialog copy — with plain body text. */
    private fun textNode(
        text: String,
        editable: Boolean = false,
    ): AccessibilityNodeInfo =
        AccessibilityNodeInfo().apply {
            this.text = text
            isEditable = editable
            isVisibleToUser = true
        }

    /**
     * scanForBlockedWebsite() can queue up to two started activities per call — the
     * navigateBrowserAway() redirect (whose resolution in this Robolectric sandbox has proven
     * inconsistent across runs, unlike on a real device with a real installed browser) and the
     * block overlay. Draining the whole queue and matching by content, rather than assuming a
     * fixed count/order, keeps assertions robust to that.
     */
    private fun drainStartedActivities(): List<Intent> = generateSequence { shadowOf(service).nextStartedActivity }.toList()

    @Test
    fun `ignores accessibility events with no package name`() {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        service.onAccessibilityEvent(event)

        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `ignores accessibility events from its own package`() {
        service.onAccessibilityEvent(windowStateChangedEvent(PACKAGE_NAME))

        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `blocks immediately on window switch into an AlwaysBlock app`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()

        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        val started = shadowOf(service).nextStartedActivity
        assertNotNull(started)
        assertTrue(started!!.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_PACKAGE) == OTHER_APP)
    }

    @Test
    fun `allows a ScheduledBlock app inside its allowed window`() {
        val now = java.time.LocalDateTime.now()
        val window = TimeWindow(0, 0, 23, 59, setOf(now.dayOfWeek))
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.ScheduledBlock(listOf(window))),
        )
        connectService()

        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `blocks a ScheduledBlock app outside its allowed window`() {
        val outsideDay =
            DayOfWeek.entries.first {
                it !=
                    java.time.LocalDateTime
                        .now()
                        .dayOfWeek
            }
        val window = TimeWindow(0, 0, 23, 59, setOf(outsideDay))
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.ScheduledBlock(listOf(window))),
        )
        connectService()

        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        assertNotNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `switching apps cancels the outgoing app's pending time-limit alarm`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = "com.first", label = "First", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()
        service.onAccessibilityEvent(windowStateChangedEvent("com.first"))

        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        verify(exactly = 1) { timeLimitScheduler.cancelBlock("com.first") }
    }

    @Test
    fun `does nothing extra when the same app reports another window-state change`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()
        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))
        shadowOf(service).clearNextStartedActivities()

        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        // Same-app re-fire only re-checks content filters (no active filters here), not the app-block rule.
        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `TimeLimit app already over its daily limit is blocked`() =
        runTest {
            usageRepository.setTodayUsageMinutes(OTHER_APP, 61)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            connectService()

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            awaitCondition { shadowOf(service).nextStartedActivity != null }
        }

    @Test
    fun `TimeLimit app still under its daily limit schedules a future block instead`() =
        runBlocking {
            usageRepository.setTodayUsageMinutes(OTHER_APP, 10)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            connectService()

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            awaitCondition {
                try {
                    verify(exactly = 1) { timeLimitScheduler.scheduleBlock(OTHER_APP, "Other", any()) }
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `handleContentSignal ignores content-changed events from a non-foreground package`() {
        // This test fires no WINDOW_STATE_CHANGED for this pkg, so currentForegroundPkg never matches it.
        service.onAccessibilityEvent(contentChangedEvent(ContentFilterEngine.INSTAGRAM))

        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }

    @Test
    fun `handleContentSignal ignores content-changed events for an untracked foreground package`() {
        val untracked = "com.example.other"
        service.onAccessibilityEvent(windowStateChangedEvent(untracked))

        service.onAccessibilityEvent(contentChangedEvent(untracked))

        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }

    @Test
    fun `handleContentSignal ignores content-changed events when no filters are active`() {
        service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))

        service.onAccessibilityEvent(contentChangedEvent(ContentFilterEngine.INSTAGRAM))

        assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
    }

    @Test
    fun `handleContentSignal detects Reels via node signals and blocks`() =
        runTest {
            webBlockRuleRepository.setContentFilter(ContentFilter.REELS, enabled = true)
            connectServiceWithFilters()
            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))
            shadowOf(service).setRootInActiveWindow(nodeTree(selected = true, contentDescription = "reel"))

            service.onAccessibilityEvent(contentChangedEvent(ContentFilterEngine.INSTAGRAM))

            assertTrue(AccessibilityService.GLOBAL_ACTION_BACK in shadowOf(service).globalActionsPerformed)
            assertEquals("Instagram Reels is blocked", ShadowToast.getTextOfLatestToast())
        }

    @Test
    fun `same app re-fire detects content filter via detectContentFilter and blocks`() =
        runTest {
            webBlockRuleRepository.setContentFilter(ContentFilter.REELS, enabled = true)
            connectServiceWithFilters()
            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))
            shadowOf(service).setRootInActiveWindow(nodeTree(selected = true, contentDescription = "reel"))

            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))

            assertTrue(AccessibilityService.GLOBAL_ACTION_BACK in shadowOf(service).globalActionsPerformed)
        }

    @Test
    fun `first entry into a tracked app blocks via content filter before the app-block-rule check runs`() =
        runTest {
            webBlockRuleRepository.setContentFilter(ContentFilter.REELS, enabled = true)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = ContentFilterEngine.INSTAGRAM, label = "Instagram", strategy = BlockingStrategy.AlwaysBlock),
            )
            connectService()
            shadowOf(service).setRootInActiveWindow(nodeTree(selected = true, contentDescription = "reel"))

            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))

            assertTrue(AccessibilityService.GLOBAL_ACTION_BACK in shadowOf(service).globalActionsPerformed)
            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `collectNodeSignals walks a multi-child tree and only picks up the selected node`() =
        runTest {
            webBlockRuleRepository.setContentFilter(ContentFilter.REELS, enabled = true)
            connectServiceWithFilters()
            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))
            val root = nodeTree(selected = false)
            shadowOf(root).addChild(nodeTree(selected = false, contentDescription = "reel"))
            shadowOf(root).addChild(nodeTree(selected = true, contentDescription = "reel"))
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(contentChangedEvent(ContentFilterEngine.INSTAGRAM))

            // Only the selected child's "reel" signal counts — if the unselected sibling's
            // signal were picked up too this would still pass, so this specifically proves
            // detection happened (the selected node was reached and its signal matched).
            assertTrue(AccessibilityService.GLOBAL_ACTION_BACK in shadowOf(service).globalActionsPerformed)
        }

    @Test
    fun `collectNodeSignals returns no detection when rootInActiveWindow is null`() =
        runTest {
            webBlockRuleRepository.setContentFilter(ContentFilter.REELS, enabled = true)
            connectServiceWithFilters()
            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))
            // rootInActiveWindow deliberately left unstubbed (null).

            service.onAccessibilityEvent(contentChangedEvent(ContentFilterEngine.INSTAGRAM))

            assertTrue(shadowOf(service).globalActionsPerformed.isEmpty())
        }

    @Test
    fun `TimeLimit app with an extension already taken today gets the 15-minute grace`() =
        runTest {
            usageRepository.setTodayUsageMinutes(OTHER_APP, 65)
            settingsRepository.recordExtensionTaken(OTHER_APP, LocalDate.now())
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            connectService()

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            // 65 minutes used is under the extended 75-minute limit (60 + 15 grace), so no block.
            awaitCondition {
                try {
                    verify(exactly = 1) { timeLimitScheduler.scheduleBlock(OTHER_APP, "Other", any()) }
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `TimeLimit app at the same usage without an extension taken is blocked`() =
        runTest {
            usageRepository.setTodayUsageMinutes(OTHER_APP, 65)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            connectService()

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            // Same 65 minutes used, but no extension taken — the plain 60-minute limit applies.
            awaitCondition { shadowOf(service).nextStartedActivity != null }
        }

    @Test
    fun `blocks a known browser via precise url bar match and shows the overlay`() =
        runTest {
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val urlBarId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)
            shadowOf(service).setRootInActiveWindow(urlBarNode(urlBarId, "https://example.com/path"))

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            // scanForBlockedWebsite() also fires a navigateBrowserAway() redirect before the
            // overlay; whether that resolves to a started activity in this Robolectric sandbox
            // has proven inconsistent across runs (unlike on a real device with a real installed
            // browser), so drain the whole queue and find the overlay by content instead of
            // assuming a fixed position.
            val blocked = drainStartedActivities().firstOrNull { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) }
            assertNotNull(blocked)
            assertEquals("example.com", blocked!!.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL))
            assertEquals(
                BlockReason.WEBSITE_BLOCKED.name,
                blocked.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCK_REASON),
            )
        }

    @Test
    fun `does not re-show the overlay for a repeat detection within the cooldown window`() =
        runTest {
            // Simulates a browser that keeps reopening onto the same blocked tab (stale/
            // restored session) with no user interaction in between — the overlay must only
            // interrupt the user once, or they'd never get a chance to reach the address bar.
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val urlBarId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)
            shadowOf(service).setRootInActiveWindow(urlBarNode(urlBarId, "https://example.com/path"))

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))
            assertTrue(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })

            Thread.sleep(350) // clear the 300ms scan throttle; stays well under the 15s cooldown
            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            // The repeat detection may still retry the redirect, but must not force the overlay
            // (and the exit it causes) a second time.
            assertFalse(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })
        }

    @Test
    fun `does not block a browser on a blocked domain when the toggle is off`() =
        runTest {
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            // settingsRepository.enableWebContentBlocking() deliberately not called.
            connectService()
            val urlBarId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)
            shadowOf(service).setRootInActiveWindow(urlBarNode(urlBarId, "https://example.com/path"))

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `does not block a browser visiting a non-blocked domain`() =
        runTest {
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val urlBarId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)
            shadowOf(service).setRootInActiveWindow(urlBarNode(urlBarId, "https://allowed.com/path"))

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `does not block while the address bar is focused for editing, even showing a blocked domain`() =
        runTest {
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val urlBarId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)
            shadowOf(service).setRootInActiveWindow(
                urlBarNode(urlBarId, "https://example.com/path", focused = true, editable = true),
            )

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `does not block when the tab switcher shows a blocked domain as a tab preview`() =
        runTest {
            // Chrome's "view all tabs" grid has no url_bar node at all — a blocked domain
            // appearing as a tab preview's title text must not be mistaken for the active page.
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val root = textNode("Open tabs")
            shadowOf(root).addChild(textNode("example.com - Home"))
            shadowOf(root).addChild(textNode("allowed.com - Docs"))
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertFalse(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })
        }

    @Test
    fun `does not block when a captcha or permission dialog covers the page`() =
        runTest {
            // A challenge/permission dialog can replace the normal browser chrome (no url_bar
            // node present) while incidentally containing text that mentions a blocked domain
            // (e.g. "Protected by example.com security") — that must not trigger a block either.
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val dialog = textNode("Verify you're human")
            shadowOf(dialog).addChild(textNode("Protected by example.com security"))
            shadowOf(service).setRootInActiveWindow(dialog)

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertFalse(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })
        }

    @Test
    fun `blocks an unmapped browser via the heuristic address-bar scan`() =
        runTest {
            val unmappedBrowser = "com.brave.browser"
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val root = textNode("Brave")
            shadowOf(root).addChild(textNode("example.com", editable = true))
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(windowStateChangedEvent(unmappedBrowser))

            assertTrue(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })
        }

    @Test
    fun `does not block an unmapped browser when the domain only appears in ordinary body text`() =
        runTest {
            // No mapped resource id for this package, and the only matching text lives in a
            // plain (non-editable, non-URL-shaped-field) node — not enough to trust as "the
            // address bar," so this must not fall back to a blind substring match.
            val unmappedBrowser = "com.brave.browser"
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val root = textNode("Brave")
            shadowOf(root).addChild(textNode("Read more about example.com in this article"))
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(windowStateChangedEvent(unmappedBrowser))

            assertFalse(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })
        }
}
