package com.focusguard.core.system.service

import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.core.domain.model.BlockReason
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.system.engine.WebContentFilterEngine
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

            // Robolectric hands back copies of child nodes, so the close-tab click itself can't be
            // observed here; the block proves the nested address bar was found.
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
