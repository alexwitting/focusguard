package com.focusguard.core.data.time

import com.focusguard.core.domain.time.ClockProvider
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class SystemClockProvider
    @Inject
    constructor() : ClockProvider {
        override fun today(): LocalDate = LocalDate.now()

        override fun now(): LocalDateTime = LocalDateTime.now()

        override fun epochMillis(): Long = System.currentTimeMillis()
    }
