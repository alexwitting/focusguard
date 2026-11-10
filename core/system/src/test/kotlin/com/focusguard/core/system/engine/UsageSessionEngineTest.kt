package com.focusguard.core.system.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsageSessionEngineTest {
    private val engine = UsageSessionEngine()

    // ── pairSessions() ──────────────────────────────────────────────────────────

    private fun event(
        pkg: String,
        type: UsageSessionEngine.EventType,
        at: Long,
    ) = UsageSessionEngine.UsageEvent(pkg, type, at)

    @Test
    fun `a session spanning three polls is counted in full`() {
        val r = UsageSessionEngine.EventType.RESUMED
        val p = UsageSessionEngine.EventType.PAUSED
        // Poll 1: app opens at 0, still open at 10.
        val first = engine.pairSessions(listOf(event("com.app", r, 0)), windowEnd = 10, selfPackage = "self", windowStart = -5)
        assertEquals(setOf("com.app"), first.stillOpen)
        // Poll 2: no events at all — the app is still in the foreground.
        val second = engine.pairSessions(emptyList(), windowEnd = 20, selfPackage = "self", windowStart = 10, openAtStart = first.stillOpen)
        assertEquals(listOf(10L to 20L), second.sessions["com.app"])
        assertEquals(setOf("com.app"), second.stillOpen)
        // Poll 3: the app is finally paused at 25.
        val third =
            engine.pairSessions(listOf(event("com.app", p, 25)), windowEnd = 30, selfPackage = "self", windowStart = 20, openAtStart = second.stillOpen)
        assertEquals(listOf(20L to 25L), third.sessions["com.app"])
        assertTrue(third.stillOpen.isEmpty())
        val total = listOf(first, second, third).sumOf { res -> res.sessions["com.app"].orEmpty().sumOf { it.second - it.first } }
        assertEquals(25L, total)
    }

    @Test
    fun `a PAUSED with no earlier event counts from the window start`() {
        val result =
            engine.pairSessions(
                listOf(event("com.app", UsageSessionEngine.EventType.PAUSED, 8)),
                windowEnd = 10,
                selfPackage = "self",
                windowStart = 2,
            )
        assertEquals(mapOf("com.app" to listOf(2L to 8L)), result.sessions)
    }

    @Test
    fun `switching activities inside one app keeps a single session`() {
        val r = UsageSessionEngine.EventType.RESUMED
        val p = UsageSessionEngine.EventType.PAUSED
        // Activity B resumes before activity A pauses (or both visible in multi-window).
        val events = listOf(event("com.app", r, 1), event("com.app", r, 3), event("com.app", p, 4), event("com.app", p, 9))
        val result = engine.pairSessions(events, windowEnd = 10, selfPackage = "self", windowStart = 0)
        assertEquals(mapOf("com.app" to listOf(1L to 9L)), result.sessions)
    }

    @Test
    fun `a stray second PAUSED and a zero-length session are both ignored`() {
        val r = UsageSessionEngine.EventType.RESUMED
        val p = UsageSessionEngine.EventType.PAUSED
        val events = listOf(event("com.a", r, 2), event("com.a", p, 6), event("com.a", p, 7), event("com.b", r, 8), event("com.b", p, 8))
        val result = engine.pairSessions(events, windowEnd = 10, selfPackage = "self", windowStart = 0)
        assertEquals(mapOf("com.a" to listOf(2L to 6L)), result.sessions)
    }

    @Test
    fun `screen off closes every open session`() {
        val events =
            listOf(
                event("com.a", UsageSessionEngine.EventType.RESUMED, 1),
                event("", UsageSessionEngine.EventType.SCREEN_OFF, 6),
            )
        val result = engine.pairSessions(events, windowEnd = 10, selfPackage = "self", windowStart = 0, openAtStart = setOf("com.b"))
        assertEquals(mapOf("com.a" to listOf(1L to 6L), "com.b" to listOf(0L to 6L)), result.sessions)
        assertTrue(result.stillOpen.isEmpty())
    }

    @Test
    fun `pairSessions pairs a resume and pause into one session`() {
        val events =
            listOf(
                UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.RESUMED, 1_000),
                UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.PAUSED, 5_000),
            )

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard").sessions

        assertEquals(mapOf("com.app" to listOf(1_000L to 5_000L)), result)
    }

    @Test
    fun `pairSessions closes a session still open at window end`() {
        val events = listOf(UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.RESUMED, 1_000))

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard").sessions

        assertEquals(mapOf("com.app" to listOf(1_000L to 10_000L)), result)
    }

    @Test
    fun `pairSessions excludes the self package`() {
        val events =
            listOf(
                UsageSessionEngine.UsageEvent("com.focusguard", UsageSessionEngine.EventType.RESUMED, 1_000),
                UsageSessionEngine.UsageEvent("com.focusguard", UsageSessionEngine.EventType.PAUSED, 5_000),
            )

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard").sessions

        assertTrue(result.isEmpty())
    }

    @Test
    fun `pairSessions ignores a PAUSED event with no matching RESUMED`() {
        val events = listOf(UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.PAUSED, 5_000))

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard").sessions

        assertTrue(result.isEmpty())
    }

    @Test
    fun `pairSessions tracks multiple packages independently`() {
        val events =
            listOf(
                UsageSessionEngine.UsageEvent("com.a", UsageSessionEngine.EventType.RESUMED, 1_000),
                UsageSessionEngine.UsageEvent("com.b", UsageSessionEngine.EventType.RESUMED, 2_000),
                UsageSessionEngine.UsageEvent("com.a", UsageSessionEngine.EventType.PAUSED, 3_000),
                UsageSessionEngine.UsageEvent("com.b", UsageSessionEngine.EventType.PAUSED, 4_000),
            )

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard").sessions

        assertEquals(
            mapOf("com.a" to listOf(1_000L to 3_000L), "com.b" to listOf(2_000L to 4_000L)),
            result,
        )
    }

    // ── shouldWarn() ─────────────────────────────────────────────────────────────

    @Test
    fun `shouldWarn is true at 80 percent usage`() {
        assertTrue(engine.shouldWarn(usedMinutes = 80, limitMinutes = 100, alreadyNotified = false))
    }

    @Test
    fun `shouldWarn is false below 80 percent`() {
        assertFalse(engine.shouldWarn(usedMinutes = 79, limitMinutes = 100, alreadyNotified = false))
    }

    @Test
    fun `shouldWarn is false once the limit is fully reached`() {
        assertFalse(engine.shouldWarn(usedMinutes = 100, limitMinutes = 100, alreadyNotified = false))
    }

    @Test
    fun `shouldWarn is false when already notified`() {
        assertFalse(engine.shouldWarn(usedMinutes = 85, limitMinutes = 100, alreadyNotified = true))
    }

    @Test
    fun `shouldWarn is false for a non-positive limit`() {
        assertFalse(engine.shouldWarn(usedMinutes = 10, limitMinutes = 0, alreadyNotified = false))
    }
}
