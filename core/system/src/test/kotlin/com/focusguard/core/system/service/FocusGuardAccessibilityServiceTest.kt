package com.focusguard.core.system.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.system.engine.ContentFilterEngine
import com.focusguard.core.system.engine.WebContentFilterEngine
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/** App blocking, content filters, time limits, bedtime — see [AccessibilityServiceTestBase] for setup. */
@RunWith(RobolectricTestRunner::class)
class FocusGuardAccessibilityServiceTest : AccessibilityServiceTestBase() {
    @Test
    fun `ignores accessibility events with no package name`() {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        service.onAccessibilityEvent(event)

        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `ignores accessibility events from its own package`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = ownPackage, label = "FocusGuard", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()

        service.onAccessibilityEvent(windowStateChangedEvent(ownPackage))
        service.onAccessibilityEvent(contentChangedEvent(ownPackage))

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
    fun `a keyboard or other non-activity window does not count as leaving the app`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = "com.first", label = "First", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()
        service.onAccessibilityEvent(windowStateChangedEvent("com.first"))

        service.onAccessibilityEvent(nonActivityWindowEvent("com.example.keyboard"))

        verify(exactly = 0) { timeLimitScheduler.cancelBlock(any()) }
    }

    @Test
    fun `returning to a blocked app from the block screen re-checks and blocks again`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()
        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))
        assertNotNull(shadowOf(service).nextStartedActivity)

        // The block screen (FocusGuard's own activity) comes up, then the user picks the blocked
        // app from Recents.
        service.onAccessibilityEvent(windowStateChangedEvent(ownPackage, "com.focusguard.BlockedActivity"))
        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        assertNotNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun `bedtime blocks an app whose own rule would allow it`() =
        runTest {
            // 2025-06-02 23:30, inside a 22:00–07:00 bedtime; the app's allowed window is all day.
            clockProvider.setNow(LocalDateTime.of(2025, 6, 2, 23, 30))
            settingsRepository.setBedtime(TimeWindow(22, 0, 7, 0))
            val allDay = TimeWindow(0, 0, 23, 59)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.ScheduledBlock(listOf(allDay))),
            )
            connectService()
            awaitField<TimeWindow?>("bedtimeCache") { it != null }

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            val started = shadowOf(service).nextStartedActivity
            assertNotNull(started)
            assertEquals(BlockReason.BEDTIME.name, started.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCK_REASON))
        }

    @Test
    fun `an app allowed now gets a block armed for when its allowed hours end`() {
        // 2025-06-02 (Monday) 16:30:00; allowed 09:00–17:00, so blocked from 17:01.
        clockProvider.setNow(LocalDateTime.of(2025, 6, 2, 16, 30))
        val window = TimeWindow(9, 0, 17, 0)
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.ScheduledBlock(listOf(window))),
        )
        connectService()

        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        assertNull(shadowOf(service).nextStartedActivity)
        verify(exactly = 1) {
            timeLimitScheduler.scheduleBlockIn(OTHER_APP, "Other", 31 * 60_000L, BlockReason.OUTSIDE_ALLOWED_WINDOW)
        }
    }

    @Test
    fun `a time-limited app gets the bedtime block when bedtime comes before the limit`() =
        runBlocking {
            // 21:50 with 50 minutes of limit left — bedtime at 22:00 comes first.
            clockProvider.setNow(LocalDateTime.of(2025, 6, 2, 21, 50))
            settingsRepository.setBedtime(TimeWindow(22, 0, 7, 0))
            usageRepository.setTodayUsageMinutes(OTHER_APP, 10)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            connectService()
            awaitField<TimeWindow?>("bedtimeCache") { it != null }

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            awaitCondition {
                try {
                    verify(exactly = 1) { timeLimitScheduler.scheduleBlockIn(OTHER_APP, "Other", 10 * 60_000L, BlockReason.BEDTIME) }
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
        }

    @Test
    fun `changing bedtime in FocusGuard re-arms the block for the app you return to`() =
        runTest {
            // 2025-06-02 (Monday) 21:00, app allowed all day, no bedtime yet → nothing armed.
            clockProvider.setNow(LocalDateTime.of(2025, 6, 2, 21, 0))
            val allDay = TimeWindow(0, 0, 23, 59)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.ScheduledBlock(listOf(allDay))),
            )
            connectService()
            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))
            verify(exactly = 0) { timeLimitScheduler.scheduleBlockIn(OTHER_APP, any(), any(), BlockReason.BEDTIME) }

            // The user opens FocusGuard (leaving the app — its alarm is dropped) and sets a bedtime
            // starting in 10 minutes.
            service.onAccessibilityEvent(windowStateChangedEvent(ownPackage, "com.focusguard.MainActivity"))
            verify { timeLimitScheduler.cancelBlock(OTHER_APP) }
            settingsRepository.setBedtime(TimeWindow(21, 10, 7, 0))
            awaitField<TimeWindow?>("bedtimeCache") { it != null }

            // Back to the app: the new bedtime is picked up and armed.
            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            verify(exactly = 1) { timeLimitScheduler.scheduleBlockIn(OTHER_APP, "Other", 10 * 60_000L, BlockReason.BEDTIME) }
        }

    @Test
    fun `a dialog inside the app already in front is handled as the same app, not a switch`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()
        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))
        shadowOf(service).clearNextStartedActivities()

        service.onAccessibilityEvent(nonActivityWindowEvent(OTHER_APP))

        assertNull(shadowOf(service).nextStartedActivity)
        verify(exactly = 0) { timeLimitScheduler.cancelBlock(OTHER_APP) }
    }

    @Test
    fun `FocusGuard's own non-activity windows (a toast, a dialog) don't count as leaving the app`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()
        service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

        service.onAccessibilityEvent(nonActivityWindowEvent(ownPackage))

        verify(exactly = 0) { timeLimitScheduler.cancelBlock(OTHER_APP) }
    }

    @Test
    fun `a window event with no class name is not treated as an app switch`() {
        blockRuleRepository.setRules(
            AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.AlwaysBlock),
        )
        connectService()

        service.onAccessibilityEvent(
            AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = OTHER_APP },
        )

        assertNull(shadowOf(service).nextStartedActivity)
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
                    verify(exactly = 1) { timeLimitScheduler.scheduleBlockIn(OTHER_APP, "Other", any(), BlockReason.TIME_LIMIT_REACHED) }
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertNull(shadowOf(service).nextStartedActivity)
        }

    @Test
    fun `if refreshing usage fails, the limit is still checked against what was recorded`() =
        runTest {
            usageRepository.setTodayUsageMinutes(OTHER_APP, 61)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            service.usageRecorder = mockk { coEvery { recordUpToNow() } throws IllegalStateException("usage stats unavailable") }
            connectService()

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))

            awaitCondition { shadowOf(service).nextStartedActivity != null }
        }

    @Test
    fun `TimeLimit check that finishes after the user left the app neither arms nor blocks`() =
        runBlocking {
            usageRepository.setTodayUsageMinutes(OTHER_APP, 10)
            blockRuleRepository.setRules(
                AppBlockRule(packageName = OTHER_APP, label = "Other", strategy = BlockingStrategy.TimeLimit(dailyLimitMinutes = 60)),
            )
            val gate = CompletableDeferred<Unit>()
            val refreshStarted = AtomicBoolean(false)
            service.usageRecorder =
                mockk {
                    coEvery { recordUpToNow() } coAnswers {
                        refreshStarted.set(true)
                        gate.await()
                    }
                }
            connectService()

            service.onAccessibilityEvent(windowStateChangedEvent(OTHER_APP))
            awaitCondition { refreshStarted.get() }
            service.onAccessibilityEvent(windowStateChangedEvent("com.example.next"))
            gate.complete(Unit)

            Thread.sleep(200)
            verify(exactly = 0) { timeLimitScheduler.scheduleBlockIn(OTHER_APP, any(), any(), any()) }
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
    fun `a content filter matches only the selected tab in a multi-child tree`() =
        runTest {
            webBlockRuleRepository.setContentFilter(ContentFilter.REELS, enabled = true)
            connectServiceWithFilters()
            service.onAccessibilityEvent(windowStateChangedEvent(ContentFilterEngine.INSTAGRAM))
            val root = nodeTree(selected = false)
            shadowOf(root).addChild(nodeTree(selected = false, contentDescription = "reel"))
            shadowOf(root).addChild(nodeTree(selected = true, contentDescription = "reel"))
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(contentChangedEvent(ContentFilterEngine.INSTAGRAM))

            // Only the selected child's "reel" signal should be matched.
            assertTrue(AccessibilityService.GLOBAL_ACTION_BACK in shadowOf(service).globalActionsPerformed)
        }

    @Test
    fun `no window content means no content-filter detection`() =
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
                    verify(exactly = 1) { timeLimitScheduler.scheduleBlockIn(OTHER_APP, "Other", any(), BlockReason.TIME_LIMIT_REACHED) }
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
}
