package com.focusguard.core.system.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsageSessionEngineTest {
    private val engine = UsageSessionEngine()

    // ── pairSessions() ──────────────────────────────────────────────────────────

    @Test
    fun `pairSessions pairs a resume and pause into one session`() {
        val events =
            listOf(
                UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.RESUMED, 1_000),
                UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.PAUSED, 5_000),
            )

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard")

        assertEquals(mapOf("com.app" to listOf(1_000L to 5_000L)), result)
    }

    @Test
    fun `pairSessions closes a session still open at window end`() {
        val events = listOf(UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.RESUMED, 1_000))

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard")

        assertEquals(mapOf("com.app" to listOf(1_000L to 10_000L)), result)
    }

    @Test
    fun `pairSessions excludes the self package`() {
        val events =
            listOf(
                UsageSessionEngine.UsageEvent("com.focusguard", UsageSessionEngine.EventType.RESUMED, 1_000),
                UsageSessionEngine.UsageEvent("com.focusguard", UsageSessionEngine.EventType.PAUSED, 5_000),
            )

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `pairSessions ignores a PAUSED event with no matching RESUMED`() {
        val events = listOf(UsageSessionEngine.UsageEvent("com.app", UsageSessionEngine.EventType.PAUSED, 5_000))

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard")

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

        val result = engine.pairSessions(events, windowEnd = 10_000, selfPackage = "com.focusguard")

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
