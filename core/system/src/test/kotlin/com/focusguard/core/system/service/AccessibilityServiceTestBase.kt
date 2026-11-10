package com.focusguard.core.system.service

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.system.alarm.TimeLimitScheduler
import com.focusguard.core.system.engine.AppBlockingEngine
import com.focusguard.core.system.engine.ContentFilterEngine
import com.focusguard.core.system.engine.DnsFilterEngine
import com.focusguard.core.system.engine.WebContentFilterEngine
import com.focusguard.core.system.foreground.UsageStatsForegroundDetector
import com.focusguard.core.system.usage.UsageRecorder
import com.focusguard.core.testing.FakeBlockRuleRepository
import com.focusguard.core.testing.FakeClockProvider
import com.focusguard.core.testing.FakeSettingsRepository
import com.focusguard.core.testing.FakeUsageRepository
import com.focusguard.core.testing.FakeWebBlockRuleRepository
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
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
abstract class AccessibilityServiceTestBase {
    protected val blockRuleRepository = FakeBlockRuleRepository()

    protected val usageRepository = FakeUsageRepository()

    protected val settingsRepository = FakeSettingsRepository()

    protected val webBlockRuleRepository = FakeWebBlockRuleRepository()

    protected val timeLimitScheduler = mockk<TimeLimitScheduler>(relaxed = true)

    protected val clockProvider = FakeClockProvider()

    protected lateinit var service: FocusGuardAccessibilityService

    companion object {
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
                blockRuleRepository = this@AccessibilityServiceTestBase.blockRuleRepository
                usageRepository = this@AccessibilityServiceTestBase.usageRepository
                settingsRepository = this@AccessibilityServiceTestBase.settingsRepository
                webBlockRuleRepository = this@AccessibilityServiceTestBase.webBlockRuleRepository
                contentFilterEngine = ContentFilterEngine()
                timeLimitScheduler = this@AccessibilityServiceTestBase.timeLimitScheduler
                clockProvider = this@AccessibilityServiceTestBase.clockProvider
                webContentFilterEngine = WebContentFilterEngine(DnsFilterEngine())
                usageStatsForegroundDetector = mockk<UsageStatsForegroundDetector>(relaxed = true)
                usageRecorder = mockk<UsageRecorder>(relaxed = true)
            }
    }

    /** A window-state event from a real Activity — registered so the service sees an app switch. */
    protected fun windowStateChangedEvent(
        pkg: String,
        activity: String = "com.example.MainActivity",
    ): AccessibilityEvent {
        shadowOf(service.packageManager).addActivityIfNotPresent(ComponentName(pkg, activity))
        return AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = pkg
            className = activity
        }
    }

    /** A window-state event from a non-Activity window (keyboard, shade, toast). */
    protected fun nonActivityWindowEvent(pkg: String): AccessibilityEvent =
        AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = pkg
            className = "android.inputmethodservice.SoftInputWindow"
        }

    protected fun contentChangedEvent(pkg: String): AccessibilityEvent =
        AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            packageName = pkg
        }

    protected fun nodeTree(
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
    protected fun awaitCondition(condition: () -> Boolean) {
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
    protected fun connectService() {
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
    protected fun connectServiceWithFilters() {
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
     * Same reasoning as connectService(), but polls both webContentBlockingEnabledCache
     * (populated by settingsRepository.isWebContentBlockingEnabled()) and webBlockRulesCache
     * (populated by webBlockRuleRepository.getWebBlockRules()) — two independent
     * onServiceConnected() Flow collectors with no ordering guarantee relative to each other.
     * Waiting on only one of them is a real race: callers set web-block rules and call
     * enableWebContentBlocking() before invoking this, but if the enabled-flag collector happens
     * to settle on Robolectric's Main dispatcher before the rules collector does, the caller's
     * very next onAccessibilityEvent() call would run scanForBlockedWebsite() against an empty
     * rules cache and silently detect nothing — an intermittent, hard-to-reproduce test failure
     * that has nothing to do with the production code under test. Callers must enable web content
     * blocking and set at least one web-block rule on the fakes before invoking this.
     */
    protected fun connectServiceWithWebBlocking() {
        val method = FocusGuardAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        method.isAccessible = true
        method.invoke(service)

        val enabledCacheField = FocusGuardAccessibilityService::class.java.getDeclaredField("webContentBlockingEnabledCache")
        enabledCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val enabledCache = enabledCacheField.get(service) as AtomicReference<Boolean>

        val rulesCacheField = FocusGuardAccessibilityService::class.java.getDeclaredField("webBlockRulesCache")
        rulesCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val rulesCache = rulesCacheField.get(service) as AtomicReference<List<WebBlockRule>>

        val deadline = System.currentTimeMillis() + 2_000
        while ((!enabledCache.get() || rulesCache.get().isEmpty()) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    protected fun urlBarNode(
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
    protected fun textNode(
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
    protected fun drainStartedActivities(): List<Intent> = generateSequence { shadowOf(service).nextStartedActivity }.toList()

    protected fun blockedOverlay(): Intent? = drainStartedActivities().firstOrNull { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) }

    protected fun chromeUrlBarId() = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)

    protected suspend fun connectWithBlockedExampleDotCom() {
        webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
        settingsRepository.enableWebContentBlocking()
        connectServiceWithWebBlocking()
    }

    /** Polls an AtomicReference field on the service until [ready] holds (async flow collectors). */
    protected fun <T> awaitField(
        name: String,
        ready: (T) -> Boolean,
    ) {
        val field = FocusGuardAccessibilityService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val ref = field.get(service) as AtomicReference<T>
        val deadline = System.currentTimeMillis() + 2_000
        while (!ready(ref.get()) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
