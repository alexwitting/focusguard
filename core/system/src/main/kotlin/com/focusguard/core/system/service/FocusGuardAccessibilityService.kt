package com.focusguard.core.system.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockDecision
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rulesCache = AtomicReference<Map<String, AppBlockRule>>(emptyMap())
    private val activeFiltersCache = AtomicReference<Set<ContentFilter>>(emptySet())
    private val currentForegroundPkg = AtomicReference("")
    private val webBlockRulesCache = AtomicReference<List<WebBlockRule>>(emptyList())
    private val webContentBlockingEnabledCache = AtomicReference(false)
    private val lastWebScanAtMs = AtomicReference(0L)
    private val lastWebBlockAction = AtomicReference("" to 0L)

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
                .isWebContentBlockingEnabled()
                .flowOn(Dispatchers.IO)
                .collect { enabled -> webContentBlockingEnabledCache.set(enabled) }
        }
        // Backup foreground signal — see UsageStatsForegroundDetector for why this exists
        // alongside the AccessibilityEvent-driven path below.
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
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(pkg, event)
            // Instagram/YouTube/TikTok swap content in place (fragment/tab change) without a
            // new window, so window-state events never fire for e.g. opening the Reels tab or
            // scrolling to the next Reel. Content-changed/scroll events catch that navigation.
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
        val nodeSignals = collectNodeSignals(rootInActiveWindow)
        val detection = contentFilterEngine.detect(pkg, className, filters, nodeSignals)
        if (detection != null) blockContentFilter(detection.label)
    }

    private fun handleWindowStateChanged(
        pkg: String,
        event: AccessibilityEvent,
    ) {
        val className = event.className?.toString()
        val prev = currentForegroundPkg.get()

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

        if (strategy is BlockingStrategy.TimeLimit) {
            serviceScope.launch(Dispatchers.IO) {
                val used = usageRepository.getTodayUsageMinutes(pkg).toLong()
                val extensionTaken =
                    settingsRepository
                        .hasTakenExtensionToday(pkg, clockProvider.today())
                        .first()
                val effectiveLimit = strategy.dailyLimitMinutes + if (extensionTaken) 15 else 0
                val effectiveRule = rule.copy(strategy = BlockingStrategy.TimeLimit(effectiveLimit))
                val decision = blockingEngine.decide(effectiveRule, now, used)
                if (decision is BlockDecision.Block) {
                    launchBlockedScreen(pkg, rule.label, decision.reason)
                } else {
                    val remaining = effectiveLimit.toLong() - used
                    timeLimitScheduler.scheduleBlock(pkg, rule.label, remaining)
                }
            }
        } else {
            val decision = blockingEngine.decide(rule, now, 0L)
            if (decision is BlockDecision.Block) {
                launchBlockedScreen(pkg, rule.label, decision.reason)
            }
        }
    }

    private fun detectContentFilter(
        pkg: String,
        className: String?,
    ): ContentFilterEngine.Detection? {
        val filters = activeFiltersCache.get()
        if (filters.isEmpty() || !ContentFilterEngine.isTrackedPackage(pkg)) return null
        return contentFilterEngine.detect(pkg, className, filters, collectNodeSignals(rootInActiveWindow))
    }

    /**
     * Walks the visible node tree collecting view-id/content-description text from the
     * currently *selected* node(s) only — e.g. the active bottom-nav tab. Instagram's "Reels"
     * tab icon/label is present in the tree on every screen (just unselected), so matching
     * against every node's text would flag the Reels tab as active from anywhere in the app.
     * Selection state is the reliable signal for "which tab/surface is actually showing."
     */
    private fun collectNodeSignals(root: AccessibilityNodeInfo?): Set<String> {
        if (root == null) return emptySet()
        val signals = mutableSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            if (node.isSelected) {
                node.viewIdResourceName?.let(signals::add)
                node.contentDescription?.toString()?.let(signals::add)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return signals
    }

    /**
     * Content-filter matches navigate back within the blocked app instead of showing the
     * full block screen — the goal is to keep the user out of the specific feed (Reels/
     * Shorts/FYP), not exit the app entirely like a full app block does.
     */
    private fun blockContentFilter(label: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(applicationContext, "$label is blocked", Toast.LENGTH_SHORT).show()
    }

    /**
     * Scans [pkg] (a known browser) for a blocked domain and, on a match, closes the tab and
     * redirects it away. Returns true if a block was detected (callers use this to skip
     * unrelated app-block-rule checks for the same event).
     *
     * Two source-independent triggers land here: the AccessibilityEvent path (immediate) and
     * the UsageStatsManager backup poll (see onServiceConnected) — both funnel through the same
     * throttle so neither can hammer the node tree.
     */
    private fun scanForBlockedWebsite(pkg: String): Boolean {
        val rules = webBlockRulesCache.get()
        if (!webContentBlockingEnabledCache.get() || rules.isEmpty()) return false

        val now = System.currentTimeMillis()
        if (now - lastWebScanAtMs.get() < WEB_SCAN_THROTTLE_MS) return false
        lastWebScanAtMs.set(now)

        val root = rootInActiveWindow ?: return false
        val urlBarResourceId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS[pkg]
        // While the address bar is being typed into, its text is user input in progress, not
        // the loaded page — matching against it would block on autocomplete/history text or the
        // still-focused previous URL before the user can type a replacement, effectively locking
        // them out of the address bar entirely. Skip scanning until editing ends (field loses
        // focus, e.g. on submit/navigate), at which point the resulting page load re-triggers a
        // normal scan against what's actually loaded.
        if (isEditingAddressBar(root, urlBarResourceId)) return false
        // For a mapped browser, the resource id not being found means we're not looking at a
        // normal page at all — the tab switcher, a captcha/permission dialog, settings, history,
        // etc. all lack the address bar entirely. Treat that as "nothing to detect," not license
        // to guess from whatever text happens to be on screen (that used to be a substring-match
        // fallback here and caused real false positives — a tab preview's title, or unrelated
        // dialog copy, could trip a block on a page the user wasn't even viewing). The heuristic
        // scan only runs for genuinely unmapped browsers, where it's the only signal available.
        val addressBarText =
            if (urlBarResourceId != null) {
                findTextByResourceId(root, urlBarResourceId)
            } else {
                findHeuristicAddressBarText(root)
            }

        val match = webContentFilterEngine.detect(addressBarText, rules) ?: return false

        // A browser can keep showing the same blocked page across reopens — session/tab
        // restore, a redirect that didn't fully land before the app backgrounded, etc. — with
        // no interaction from the user in between. If every one of those detections re-launched
        // the full-screen overlay, the browser would become unusable: it exits before the user
        // has any chance to tap the address bar (which is the one thing that pauses scanning,
        // above) or open a new tab. Retry the tab-close/redirect every time regardless, but only
        // force the disruptive overlay once per cooldown window per package — that leaves a real
        // gap where the browser stays on screen (even mid-redirect) long enough to interact with.
        val (lastBlockedPkg, lastBlockedAt) = lastWebBlockAction.get()
        val isRepeatDetection = pkg == lastBlockedPkg && now - lastBlockedAt < WEB_BLOCK_COOLDOWN_MS
        lastWebBlockAction.set(pkg to now)

        closeBrowserTab(root)
        navigateBrowserAway(pkg)
        if (!isRepeatDetection) {
            launchBlockedScreen(pkg, match.domain, BlockReason.WEBSITE_BLOCKED)
        }
        return true
    }

    /**
     * Redirects the browser's current tab to a neutral page instead of leaving it sitting on
     * the blocked URL. Without this, reopening the browser lands straight back on the blocked
     * page — which re-triggers the block immediately, before the user can type a new address or
     * open a new tab, effectively locking them out of the browser entirely. ACTION_VIEW targeted
     * at the browser's own package is a universal mechanism every browser handles, unlike
     * [closeBrowserTab]'s UI-automation approach, which isn't guaranteed to find anything (e.g.
     * no "close tab" affordance exists at all with only one tab open).
     */
    private fun navigateBrowserAway(pkg: String) {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_REDIRECT_URL)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        runCatching { startActivity(intent) }
    }

    /**
     * True if the address bar (or any editable field) currently has input focus — i.e. the user
     * is actively typing rather than viewing a loaded page. [urlBarResourceId] gets a precise
     * check when known; any focused+editable node elsewhere also counts, since typing anywhere
     * in the browser chrome (or an unmapped browser's own address field) shouldn't trigger a
     * block either.
     */
    private fun isEditingAddressBar(
        root: AccessibilityNodeInfo,
        urlBarResourceId: String?,
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            val isUrlBarNode = urlBarResourceId != null && node.viewIdResourceName == urlBarResourceId
            val isCandidateField = isUrlBarNode || node.isEditable
            if (node.isVisibleToUser && node.isFocused && isCandidateField) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    /** Bounded BFS for the text of the first visible node matching [resourceId], or null. */
    private fun findTextByResourceId(
        root: AccessibilityNodeInfo,
        resourceId: String,
    ): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && node.viewIdResourceName == resourceId) return node.text?.toString()
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    /**
     * For browsers not in [WebContentFilterEngine.URL_BAR_RESOURCE_IDS], there's no known
     * resource id to look up, so this is the only detection signal available at all. Rather than
     * scanning every visible string (which is what caused the tab-switcher/dialog false
     * positives for mapped browsers), this only ever returns text from a node that's plausibly
     * *the address bar itself* — visible, editable-looking, and shaped like a URL per
     * [WebContentFilterEngine.looksLikeUrl] — never a tab-preview title, dialog copy, or other
     * incidental on-screen text. Returns null (no detection) if nothing matches that description.
     */
    private fun findHeuristicAddressBarText(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()
            val looksLikeAddressBar = node.isEditable || node.className?.contains("EditText") == true
            val isVisibleCandidate = node.isVisibleToUser && looksLikeAddressBar
            if (isVisibleCandidate && text != null && WebContentFilterEngine.looksLikeUrl(text)) {
                return text
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    /**
     * Best-effort: finds a clickable "close tab" node and taps it, so the blocked tab isn't
     * still sitting there when the user returns to the browser after dismissing the overlay.
     * Browsers vary and this content-description isn't a stable API either — if nothing is
     * found, this is a no-op; the block overlay is what actually removes the page from view.
     */
    private fun closeBrowserTab(root: AccessibilityNodeInfo) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            val description = node.contentDescription?.toString()
            val isCloseTabCandidate = node.isVisibleToUser && node.isClickable
            if (isCloseTabCandidate && description != null && description.contains("close tab", ignoreCase = true)) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
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
        private const val MAX_NODES_SCANNED = 300
        private const val WEB_SCAN_THROTTLE_MS = 300L
        private const val WEB_BLOCK_COOLDOWN_MS = 15_000L
        private const val USAGE_STATS_POLL_INTERVAL_MS = 2_000L
        private const val SAFE_REDIRECT_URL = "https://www.google.com/"
    }
}
