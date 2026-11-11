package com.focusguard.core.system.service

import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.core.system.engine.WebContentFilterEngine

/** Most nodes looked at per walk, so a huge page can't stall the accessibility thread. */
internal const val MAX_NODES_SCANNED = 300

/** Breadth-first walk of this node and its descendants, stopping after [MAX_NODES_SCANNED]. */
internal fun AccessibilityNodeInfo.walk(): Sequence<AccessibilityNodeInfo> =
    sequence {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(this@walk)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            yield(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
    }

/**
 * View ids and content descriptions of *selected* nodes only (e.g. the active tab). Unselected
 * tabs like Reels are always in the tree, so only selection shows what's on screen.
 */
internal fun AccessibilityNodeInfo?.selectedNodeSignals(): Set<String> =
    this
        ?.walk()
        ?.filter { it.isSelected }
        ?.flatMap { listOfNotNull(it.viewIdResourceName, it.contentDescription?.toString()) }
        ?.toSet()
        .orEmpty()

/** True while the address bar ([urlBarResourceId]), or any other editable field, has input focus. */
internal fun AccessibilityNodeInfo.hasFocusedInput(urlBarResourceId: String?): Boolean =
    walk().any { node ->
        val isUrlBar = urlBarResourceId != null && node.viewIdResourceName == urlBarResourceId
        node.isVisibleToUser && node.isFocused && (isUrlBar || node.isEditable)
    }

/** Text of the first visible node with [resourceId], or null. */
internal fun AccessibilityNodeInfo.visibleTextOf(resourceId: String): String? =
    walk()
        .firstOrNull { it.isVisibleToUser && it.viewIdResourceName == resourceId }
        ?.text
        ?.toString()

/**
 * Address-bar text for browsers with no known address-bar id: the first visible, editable node
 * whose text looks like a URL. Other on-screen text is ignored. Null if none.
 */
internal fun AccessibilityNodeInfo.heuristicAddressBarText(): String? =
    walk()
        .filter { it.isVisibleToUser && (it.isEditable || it.className?.contains("EditText") == true) }
        .mapNotNull { it.text?.toString() }
        .firstOrNull(WebContentFilterEngine::looksLikeUrl)

/** Best effort: taps a visible "close tab" button if the browser shows one. */
internal fun AccessibilityNodeInfo.clickCloseTab() {
    walk()
        .firstOrNull { node ->
            node.isVisibleToUser &&
                node.isClickable &&
                node.contentDescription?.toString()?.contains("close tab", ignoreCase = true) == true
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}
