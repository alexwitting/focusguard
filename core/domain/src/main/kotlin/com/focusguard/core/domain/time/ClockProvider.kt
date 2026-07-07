package com.focusguard.core.domain.time

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The single seam for "what time is it right now" — anything that needs the current date/time
 * for business logic (not pure UI display) should go through this instead of calling
 * `LocalDate.now()`/`LocalDateTime.now()`/`System.currentTimeMillis()` directly, so tests can
 * control it and midnight-rollover behavior is consistent everywhere it matters.
 */
interface ClockProvider {
    fun today(): LocalDate

    fun now(): LocalDateTime

    fun epochMillis(): Long
}
