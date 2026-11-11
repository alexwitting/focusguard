package com.focusguard.core.system.service

import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.system.engine.WebContentFilterEngine
import com.focusguard.core.system.foreground.UsageStatsForegroundDetector
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.LocalDateTime

/** Website blocking via the browser scan — see [AccessibilityServiceTestBase] for setup. */
@RunWith(RobolectricTestRunner::class)
class FocusGuardAccessibilityServiceWebTest : AccessibilityServiceTestBase() {
    @Test
    fun `finds the address bar nested inside the page tree, alongside a close-tab button`() =
        runTest {
            connectWithBlockedExampleDotCom()
            val closeTab =
                AccessibilityNodeInfo().apply {
                    contentDescription = "Close tab"
                    isClickable = true
                    isVisibleToUser = true
                }
            val root =
                AccessibilityNodeInfo().apply { isVisibleToUser = true }.also { r ->
                    val toolbar = AccessibilityNodeInfo().apply { isVisibleToUser = true }
                    shadowOf(toolbar).addChild(urlBarNode(chromeUrlBarId(), "https://example.com/a"))
                    shadowOf(r).addChild(toolbar)
                    shadowOf(r).addChild(closeTab)
                }
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            // Robolectric returns copies of child nodes, so the click itself can't be observed.
            assertNotNull(blockedOverlay())
        }

    @Test
    fun `an address bar that isn't visible on screen doesn't trigger a block`() =
        runTest {
            connectWithBlockedExampleDotCom()
            shadowOf(service).setRootInActiveWindow(
                urlBarNode(chromeUrlBarId(), "https://example.com/a").apply { isVisibleToUser = false },
            )

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertNull(blockedOverlay())
        }

    @Test
    fun `typing in some other field of the browser also pauses scanning`() =
        runTest {
            connectWithBlockedExampleDotCom()
            val root =
                urlBarNode(chromeUrlBarId(), "https://example.com/a").also { r ->
                    shadowOf(r).addChild(textNode("find in page", editable = true).apply { isFocused = true })
                }
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            assertNull(blockedOverlay())
        }

    @Test
    fun `scans are throttled so rapid content events don't each walk the page tree`() =
        runTest {
            clockProvider.setNow(LocalDateTime.of(2026, 3, 10, 12, 0))
            connectWithBlockedExampleDotCom()
            shadowOf(service).setRootInActiveWindow(urlBarNode(chromeUrlBarId(), "https://news.other.org"))
            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            // Same instant: the page now shows a blocked site, but the throttle skips this scan.
            shadowOf(service).setRootInActiveWindow(urlBarNode(chromeUrlBarId(), "https://example.com/a"))
            service.onAccessibilityEvent(contentChangedEvent(WebContentFilterEngine.CHROME))
            assertNull(blockedOverlay())

            // A moment later it's scanned again and blocked.
            clockProvider.setNow(LocalDateTime.of(2026, 3, 10, 12, 0, 1))
            service.onAccessibilityEvent(contentChangedEvent(WebContentFilterEngine.CHROME))
            assertNotNull(blockedOverlay())
        }

    @Test
    fun `the heuristic scan ignores hidden or non-editable URL-looking text in unmapped browsers`() =
        runTest {
            connectWithBlockedExampleDotCom()
            val root =
                AccessibilityNodeInfo().apply { isVisibleToUser = true }.also { r ->
                    shadowOf(r).addChild(textNode("example.com", editable = true).apply { isVisibleToUser = false })
                    shadowOf(r).addChild(textNode("example.com", editable = false))
                }
            shadowOf(service).setRootInActiveWindow(root)

            service.onAccessibilityEvent(windowStateChangedEvent("com.brave.browser"))

            assertNull(blockedOverlay())
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

            // The redirect may also start an activity, so find the block screen by content.
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
            // The browser keeps reopening on the same blocked tab: block screen only once.
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            settingsRepository.enableWebContentBlocking()
            connectServiceWithWebBlocking()
            val urlBarId = WebContentFilterEngine.URL_BAR_RESOURCE_IDS.getValue(WebContentFilterEngine.CHROME)
            shadowOf(service).setRootInActiveWindow(urlBarNode(urlBarId, "https://example.com/path"))

            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))
            assertTrue(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })

            Thread.sleep(350) // clear the 300ms scan throttle; stays well under the 15s cooldown
            service.onAccessibilityEvent(windowStateChangedEvent(WebContentFilterEngine.CHROME))

            // The redirect may repeat, but the block screen must not.
            assertFalse(drainStartedActivities().any { it.hasExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL) })
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
            // The tab grid has no address bar; a tab title showing a blocked domain doesn't count.
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
            // A dialog replacing the browser UI can mention a blocked domain without being on it.
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
            // Unknown browser: plain body text mentioning the domain isn't an address bar.
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

    @Test
    fun `the backup poll catches a blocked site the browser never reported an event for`() =
        runTest {
            service.usageStatsForegroundDetector =
                mockk { every { currentForegroundBrowser() } returns WebContentFilterEngine.CHROME }
            connectWithBlockedExampleDotCom()
            shadowOf(service).setRootInActiveWindow(urlBarNode(chromeUrlBarId(), "https://example.com/feed"))

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

            assertEquals("example.com", blockedOverlay()?.getStringExtra(FocusGuardAccessibilityService.EXTRA_BLOCKED_LABEL))
        }

    @Test
    fun `the backup poll stays idle while the browser scan is off`() =
        runTest {
            val detector = mockk<UsageStatsForegroundDetector>(relaxed = true)
            service.usageStatsForegroundDetector = detector
            webBlockRuleRepository.setRules(WebBlockRule(domain = "example.com"))
            connectService()

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))

            verify(exactly = 0) { detector.currentForegroundBrowser() }
            assertNull(blockedOverlay())
        }

    @Test
    fun `after shutdown the backup poll no longer runs`() =
        runTest {
            val detector = mockk<UsageStatsForegroundDetector>(relaxed = true)
            service.usageStatsForegroundDetector = detector
            connectWithBlockedExampleDotCom()

            service.onDestroy()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))

            verify(exactly = 0) { detector.currentForegroundBrowser() }
            assertNull(FocusGuardAccessibilityService.instance)
        }
}
