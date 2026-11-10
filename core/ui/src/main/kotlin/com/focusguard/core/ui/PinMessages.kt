package com.focusguard.core.ui

import com.focusguard.core.domain.model.PinAttemptResult

/** Tries-left count is only worth showing once it's getting low. */
private const val WARN_AT_ATTEMPTS_LEFT = 2
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** The line shown under the PIN dots for a failed attempt; null on success. */
fun PinAttemptResult.errorMessage(): String? =
    when (this) {
        is PinAttemptResult.Success -> null
        is PinAttemptResult.Incorrect ->
            when {
                attemptsLeft > WARN_AT_ATTEMPTS_LEFT -> "Incorrect PIN"
                attemptsLeft == 1 -> "Incorrect PIN · 1 try left"
                else -> "Incorrect PIN · $attemptsLeft tries left"
            }
        is PinAttemptResult.LockedOut -> "Too many tries. Try again in ${formatWait(retryInMillis)}"
    }

/** "30s", "2 min" — rounded up so it never says "0s". */
internal fun formatWait(millis: Long): String {
    val seconds = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    return if (seconds < SECONDS_PER_MINUTE) {
        "${seconds}s"
    } else {
        "${(seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE} min"
    }
}
