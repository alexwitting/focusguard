package com.focusguard.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Settable clock for :core:domain's own tests (which can't depend on :core:testing). Starts at
 * the real current time, so tokens issued with Instant.now() in older tests stay valid.
 */
class TestClock(
    var now: Instant = Instant.now(),
) : ClockProvider {
    override fun today(): LocalDate = now().toLocalDate()

    override fun now(): LocalDateTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault())

    override fun epochMillis(): Long = now.toEpochMilli()
}
