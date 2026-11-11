package com.focusguard.core.system.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockDecision
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.ExtensionPolicy
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.system.alarm.TimeLimitScheduler
import com.focusguard.core.system.engine.AppBlockingEngine
import com.focusguard.core.system.engine.ContentFilterEngine
import com.focusguard.core.system.engine.WebContentFilterEngine
import com.focusguard.core.system.foreground.UsageStatsForegroundDetector
import com.focusguard.core.system.usage.UsageRecorder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class FocusGuardAccessibilityService : AccessibilityService() {
    @Inject lateinit var blockingEngine: AppBlockingEngine

    @Inject lateinit var blockRuleRepository: BlockRuleRepository

    @Inject lateinit var usageRepository: UsageRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var webBlockRuleRepository: WebBlockRuleRepository

    @Inject lateinit var contentFilterEngine: ContentFilterEngine

    @Inject lateinit var timeLimitScheduler: TimeLimitScheduler

    @Inject lateinit var clockProvider: ClockProvider

    @Inject lateinit var webContentFilterEngine: WebContentFilterEngine

    @Inject lateinit var usageStatsForegroundDetector: UsageStatsForegroundDetector

    @Inject lateinit var usageRecorder: UsageRecorder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rulesCache = AtomicReference<Map<String, AppBlockRule>>(emptyMap())
    private val activeFiltersCache = AtomicReference<Set<ContentFilter>>(emptySet())
    private val currentForegroundPkg = AtomicReference("")
    private val webBlockRulesCache = AtomicReference<List<WebBlockRule>>(emptyList())
    private val webContentBlockingEnabledCache = AtomicReference(false)
    private val lastWebScanAtMs = AtomicReference(0L)
    private val lastWebBlockAction = AtomicReference("" to 0L)
    private val bedtimeCache = AtomicReference<TimeWindow?>(null)
    private val activityWindowCache = ConcurrentHashMap<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        serviceScope.launch {
            blockRuleRepository
                .getAppBlockRules()
                .flowOn(Dispatchers.IO)
                .collect { rules ->
                    rulesCache.set(rules.filter { it.enabled }.associateBy { it.packageName })
                }
        }
        serviceScope.launch {
            webBlockRuleRepository
                .getContentFilters()
                .flowOn(Dispatchers.IO)
                .collect { filters -> activeFiltersCache.set(filters) }
        }
        serviceScope.launch {
            webBlockRuleRepository
                .getWebBlockRules()
                .flowOn(Dispatchers.IO)
                .collect { rules -> webBlockRulesCache.set(rules) }
        }
        serviceScope.launch {
            settingsRepository
                .getBedtime()
                .flowOn(Dispatchers.IO)
                .collect { window -> bedtimeCache.set(window) }
        }
        serviceScope.launch {
            settingsRepository
                .isWebContentBlockingEnabled()
                .flowOn(Dispatchers.IO)
                .collect { enabled -> webContentBlockingEnabledCache.set(enabled) }
        }
        // Backup browser check for when accessibility events are delayed or throttled.
        serviceScope.launch {
            while (isActive) {
                delay(USAGE_STATS_POLL_INTERVAL_MS)
                if (webContentBlockingEnabledCache.get()) {
                    usageStatsForegroundDetector.currentForegroundBrowser()?.let(::scanForBlockedWebsite)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) handleOwnWindow(event)
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(pkg, event)
            // In-app navigation (e.g. opening the Reels tab) swaps content without a new window,
            // so it only shows up as content-changed or scroll events.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            -> handleContentSignal(pkg, event)
        }
    }

    private fun handleContentSignal(
        pkg: String,
        event: AccessibilityEvent,
    ) {
        if (currentForegroundPkg.get() != pkg) return
        if (pkg in WebContentFilterEngine.KNOWN_BROWSERS && scanForBlockedWebsite(pkg)) return
        if (!ContentFilterEngine.isTrackedPackage(pkg)) return
        val filters = activeFiltersCache.get()
        if (filters.isEmpty()) return

        val className = event.className?.toString()
        val nodeSignals = rootInActiveWindow.selectedNodeSignals()
        val detection = contentFilterEngine.detect(pkg, className, filters, nodeSignals)
        if (detection != null) blockContentFilter(detection.label)
    }

    /**
     * FocusGuard's own screens count as leaving the previous app, so returning to it (e.g. from
     * Recents) re-checks its rule and re-arms its alarm.
     */
    private fun handleOwnWindow(event: AccessibilityEvent) {
        if (!isActivityWindow(packageName, event.className?.toString())) return
        val prev = currentForegroundPkg.getAndSet(packageName)
        if (prev.isNotEmpty() && prev != packageName) timeLimitScheduler.cancelBlock(prev)
    }

    /**
     * Whether a window-state event comes from an Activity of [pkg]. The keyboard, notification
     * shade, toasts and dialogs fire the same event but aren't app switches.
     */
    private fun isActivityWindow(
        pkg: String,
        className: String?,
    ): Boolean {
        if (className.isNullOrEmpty()) return false
        return activityWindowCache.getOrPut("$pkg/$className") {
            runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.isSuccess
        }
    }

    private fun handleWindowStateChanged(
        pkg: String,
        event: AccessibilityEvent,
    ) {
        val className = event.className?.toString()
        val prev = currentForegroundPkg.get()

        // Keyboard, shade or toast from another package: not an app switch.
        if (prev != pkg && !isActivityWindow(pkg, className)) return

        if (prev == pkg) {
            // Same app still in foreground — check in-app navigation to short-form content.
            val detection = detectContentFilter(pkg, className)
            if (detection != null) {
                blockContentFilter(detection.label)
                return
            }
            if (pkg in WebContentFilterEngine.KNOWN_BROWSERS) scanForBlockedWebsite(pkg)
            return
        }

        // App switch — cancel any timer running for the outgoing app.
        if (prev.isNotEmpty()) timeLimitScheduler.cancelBlock(prev)
        currentForegroundPkg.set(pkg)

        // 1. Content filter check (Instagram Reels, YouTube Shorts, TikTok FYP).
        val detection = detectContentFilter(pkg, className)
        if (detection != null) {
            blockContentFilter(detection.label)
            return
        }

        // 2. Website content-block check — independent of the app-block rule below, so a
        // browser can be both website-scanned and separately app-blocked (e.g. a time limit).
        if (pkg in WebContentFilterEngine.KNOWN_BROWSERS && scanForBlockedWebsite(pkg)) return

        // 3. App block rule check.
        val rule = rulesCache.get()[pkg] ?: return
        val now = clockProvider.now()
        val strategy = rule.strategy

        val bedtime = bedtimeCache.get()

        if (strategy is BlockingStrategy.TimeLimit) {
            serviceScope.launch(Dispatchers.IO) { checkTimeLimit(pkg, rule, strategy, now, bedtime) }
        } else {
            val decision = blockingEngine.decide(rule, now, 0L, bedtime)
            if (decision is BlockDecision.Block) {
                launchBlockedScreen(pkg, rule.label, decision.reason)
            } else {
                // Arm a block for when allowed hours end or bedtime starts while the app is open.
                blockingEngine.nextBlock(rule, now, bedtime)?.let {
                    timeLimitScheduler.scheduleBlockIn(pkg, rule.label, it.delay.toMillis(), it.reason)
                }
            }
        }
    }

    /** Blocks [pkg] if today's usage is over its limit, otherwise arms the next block. */
    private suspend fun checkTimeLimit(
        pkg: String,
        rule: AppBlockRule,
        strategy: BlockingStrategy.TimeLimit,
        now: LocalDateTime,
        bedtime: TimeWindow?,
    ) {
        // Bring recorded usage up to now; the poller only writes every ~15 minutes.
        runCatching { usageRecorder.recordUpToNow() }.onFailure { Log.w(TAG, "Usage refresh failed", it) }
        val used = usageRepository.getTodayUsageMinutes(pkg).toLong()
        val extensionTaken =
            settingsRepository
                .hasTakenExtensionToday(pkg, clockProvider.today())
                .first()
        val effectiveLimit = strategy.dailyLimitMinutes + if (extensionTaken) ExtensionPolicy.MINUTES else 0
        val effectiveRule = rule.copy(strategy = BlockingStrategy.TimeLimit(effectiveLimit))
        val decision = blockingEngine.decide(effectiveRule, now, used, bedtime)
        // The user may have left the app while usage was loading; its alarm was already
        // cancelled on that switch, so arming or blocking now would hit the wrong app.
        if (currentForegroundPkg.get() != pkg) return
        if (decision is BlockDecision.Block) {
            launchBlockedScreen(pkg, rule.label, decision.reason)
        } else {
            val limitDelayMs = (effectiveLimit.toLong() - used) * MILLIS_PER_MINUTE
            val upcoming = blockingEngine.nextBlock(rule, now, bedtime)
            // One alarm per app: arm whichever comes first, the limit or bedtime.
            if (upcoming != null && upcoming.delay.toMillis() < limitDelayMs) {
                timeLimitScheduler.scheduleBlockIn(pkg, rule.label, upcoming.delay.toMillis(), upcoming.reason)
            } else {
                timeLimitScheduler.scheduleBlockIn(pkg, rule.label, limitDelayMs, BlockReason.TIME_LIMIT_REACHED)
            }
        }
    }

    private fun detectContentFilter(
        pkg: String,
        className: String?,
    ): ContentFilterEngine.Detection? {
        val filters = activeFiltersCache.get()
        if (filters.isEmpty() || !ContentFilterEngine.isTrackedPackage(pkg)) return null
        return contentFilterEngine.detect(pkg, className, filters, rootInActiveWindow.selectedNodeSignals())
    }

    /** Navigates back out of the filtered feed, leaving the rest of the app usable. */
    private fun blockContentFilter(label: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(applicationContext, "$label is blocked", Toast.LENGTH_SHORT).show()
    }

    /**
     * Checks a known browser's address bar for a blocked domain; on a match closes the tab,
     * redirects it and shows the block screen. Returns true if blocked. Throttled, since both
     * accessibility events and the backup poll call it.
     */
    private fun scanForBlockedWebsite(pkg: String): Boolean {
        val rules = webBlockRulesCache.get()
        if (!webContentBlockingEnabledCache.get() || rules.isEmpty()) return false

        val now = clockProvider.epochMillis()
        if (now - lastWebScanAtMs.get() < WEB_SCAN_THROTTLE_MS) return false
        lastWebScanAtMs.set(now)

        val root = rootInActiveWindow ?: return false
        val urlBarResourceId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS[pkg]
        // While the user is typing, the address bar holds their input, not the loaded page.
        if (root.hasFocusedInput(urlBarResourceId)) return false
        // Known browser without its address bar on screen (tab switcher, dialog): nothing to
        // check. The heuristic scan is only for browsers with no known address-bar id.
        val addressBarText =
            if (urlBarResourceId != null) {
                root.visibleTextOf(urlBarResourceId)
            } else {
                root.heuristicAddressBarText()
            }

        val match = webContentFilterEngine.detect(addressBarText, rules) ?: return false

        // The same blocked page can reappear on reopen (tab restore). Always close/redirect it,
        // but show the block screen at most once per cooldown so the browser stays usable.
        val (lastBlockedPkg, lastBlockedAt) = lastWebBlockAction.get()
        val isRepeatDetection = pkg == lastBlockedPkg && now - lastBlockedAt < WEB_BLOCK_COOLDOWN_MS
        lastWebBlockAction.set(pkg to now)

        root.clickCloseTab()
        navigateBrowserAway(pkg)
        if (!isRepeatDetection) {
            launchBlockedScreen(pkg, match.domain, BlockReason.WEBSITE_BLOCKED)
        }
        return true
    }

    /**
     * Sends the browser to a neutral page so reopening it doesn't land on the blocked URL. Works
     * in every browser, unlike [clickCloseTab].
     */
    private fun navigateBrowserAway(pkg: String) {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_REDIRECT_URL)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    internal fun launchBlockedScreen(
        blockedPackage: String,
        label: String,
        reason: BlockReason,
    ) {
        val intent =
            Intent().apply {
                setClassName(packageName, "com.focusguard.BlockedActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackage)
                putExtra(EXTRA_BLOCKED_LABEL, label)
                putExtra(EXTRA_BLOCK_REASON, reason.name)
            }
        startActivity(intent)
    }

    companion object {
        @Volatile var instance: FocusGuardAccessibilityService? = null
            private set

        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_LABEL = "extra_blocked_label"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
        private const val WEB_SCAN_THROTTLE_MS = 300L
        private const val WEB_BLOCK_COOLDOWN_MS = 15_000L
        private const val USAGE_STATS_POLL_INTERVAL_MS = 2_000L
        private const val SAFE_REDIRECT_URL = "https://www.google.com/"
        private const val TAG = "FocusGuardA11y"
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
