package com.focusguard.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class AppUsageRecordTest {
    @Test
    fun `durationMinutes computes whole minutes between start and end`() {
        val session =
            UsageSession(
                startTime = Instant.ofEpochMilli(0),
                endTime = Instant.ofEpochMilli(90_000),
            )

        assertEquals(1, session.durationMinutes)
    }

    @Test
    fun `durationMinutes rounds down a partial minute`() {
        val session =
            UsageSession(
                startTime = Instant.ofEpochMilli(0),
                endTime = Instant.ofEpochMilli(59_000),
            )

        assertEquals(0, session.durationMinutes)
    }
}
