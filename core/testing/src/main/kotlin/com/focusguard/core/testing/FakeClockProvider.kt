package com.focusguard.core.testing

import com.focusguard.core.domain.time.ClockProvider
import java.time.LocalDate
import java.time.LocalDateTime

/** Defaults to real wall-clock time so most tests need no setup; call the setters to pin it. */
class FakeClockProvider : ClockProvider {
    private var fixedNow: LocalDateTime? = null

    fun setNow(now: LocalDateTime) {
        fixedNow = now
    }

    override fun today(): LocalDate = (fixedNow ?: LocalDateTime.now()).toLocalDate()

    override fun now(): LocalDateTime = fixedNow ?: LocalDateTime.now()

    override fun epochMillis(): Long =
        fixedNow?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()
}
