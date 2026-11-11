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
 * Shared setup for the accessibility service tests. The service runs under Robolectric with its
 * `@Inject` fields assigned directly (no Hilt test component).
 *
 * Uses Robolectric's JUnit4 runner rather than the JUnit5 bridge, which doesn't isolate these
 * tests from each other. TimeLimit decisions run on Dispatchers.IO, so those tests poll with
 * [awaitCondition].
 */
abstract class AccessibilityServiceTestBase {
    protected val blockRuleRepository = FakeBlockRuleRepository()

    protected val usageRepository = FakeUsageRepository()

    protected val settingsRepository = FakeSettingsRepository()

    protected val webBlockRuleRepository = FakeWebBlockRuleRepository()

    protected val timeLimitScheduler = mockk<TimeLimitScheduler>(relaxed = true)

    protected val clockProvider = FakeClockProvider()

    protected lateinit var service: FocusGuardAccessibilityService

    /** The service's own package: the module namespace under Robolectric, not the app's. */
    protected val ownPackage: String get() = service.packageName

    companion object {
        const val OTHER_APP = "com.other.app"
    }

    @Before
    fun setUp() {
        // get(), not create(): create() runs Hilt's injection, which needs a Hilt test app.
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
     * Polls for up to 2 seconds. Calls [condition] once per iteration, since some checks (like
     * `nextStartedActivity`) consume what they read.
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
     * Calls the protected onServiceConnected() and waits until the rules cache is filled. Callers
     * must set at least one rule first.
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

    /** Like [connectService], but waits for the content-filter cache. */
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
     * Like [connectService], but waits for both the browser-scan flag and the website rules,
     * which load independently. Callers must enable the scan and set a website rule first.
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

    /** A plain-text node that isn't an address bar, like a tab title or dialog text. */
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
     * Every activity started so far. A browser block can start the redirect as well as the block
     * screen, so tests look for the block screen by content.
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
