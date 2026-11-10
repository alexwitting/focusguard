package com.focusguard.core.domain.model

/** How much usage history is kept on the device. */
object UsageRetention {
    /**
     * Days of history kept. The app shows the last 7; 30 leaves room for a monthly view without
     * letting the database grow forever.
     */
    const val DAYS = 30L
}
