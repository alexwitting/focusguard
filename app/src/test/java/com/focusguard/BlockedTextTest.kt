package com.focusguard

import com.focusguard.core.domain.model.BlockReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockedTextTest {
    @Test
    fun `the app's name is used when known`() {
        assertEquals(BlockedText("Instagram is blocked", "You can change this in your block list."), blockedText(BlockReason.ALWAYS_BLOCKED, "Instagram"))
        assertEquals("The rest of YouTube still works.", blockedText(BlockReason.CONTENT_FILTER, "YouTube").body)
    }

    @Test
    fun `an unknown app reads as this app, capitalised only at the start of a sentence`() {
        assertEquals("You've used today's limit for this app", blockedText(BlockReason.TIME_LIMIT_REACHED, null).headline)
        assertEquals("This app is blocked right now", blockedText(BlockReason.OUTSIDE_ALLOWED_WINDOW, null).headline)
        assertEquals("The rest of this app still works.", blockedText(BlockReason.CONTENT_FILTER, null).body)
        assertEquals("This app is blocked until bedtime ends.", blockedText(BlockReason.BEDTIME, null).body)
    }

    @Test
    fun `every reason has text`() {
        BlockReason.entries.forEach { reason ->
            val text = blockedText(reason, null)
            assert(text.headline.isNotBlank() && text.body.isNotBlank()) { reason }
        }
    }
}
