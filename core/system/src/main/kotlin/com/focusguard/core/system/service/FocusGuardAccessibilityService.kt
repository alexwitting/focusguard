package com.focusguard.core.system.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockDecision
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.repository.BlockRuleRepository
import com.focusguard.core.domain.repository.SettingsRepository
import com.focusguard.core.domain.repository.UsageRepository
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import com.focusguard.core.domain.time.ClockProvider
import com.focusguard.core.system.alarm.TimeLimitScheduler
import com.focusguard.core.system.engine.AppBlockingEngine
import com.focusguard.core.system.engine.ContentFilterEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rulesCache = AtomicReference<Map<String, AppBlockRule>>(emptyMap())
    private val activeFiltersCache = AtomicReference<Set<ContentFilter>>(emptySet())
    private val currentForegroundPkg = AtomicReference("")

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
            if (detection != null) blockContentFilter(detection.label)
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

        // 2. App block rule check.
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
    }
}
