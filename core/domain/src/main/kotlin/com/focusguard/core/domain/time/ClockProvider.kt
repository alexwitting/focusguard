package com.focusguard.core.domain.time

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The current date and time for business logic. Use this instead of `LocalDate.now()`,
 * `LocalDateTime.now()` or `System.currentTimeMillis()` so tests can control time.
 */
interface ClockProvider {
    fun today(): LocalDate

    fun now(): LocalDateTime

    fun epochMillis(): Long
}
