package com.focusguard.core.domain.model

/** Wrong-PIN bookkeeping, persisted so restarting the app doesn't reset it. */
data class PinLockout(
    val failedAttempts: Int = 0,
    /** Epoch millis until which every attempt is refused; 0 when not locked. */
    val lockedUntilEpochMillis: Long = 0,
)

/** The outcome of one PIN entry. */
sealed interface PinAttemptResult {
    data class Success(
        val token: PinVerificationToken,
    ) : PinAttemptResult

    /** Wrong PIN; [attemptsLeft] more wrong ones are allowed before a lockout starts. */
    data class Incorrect(
        val attemptsLeft: Int,
    ) : PinAttemptResult

    /** Too many wrong PINs — nothing is checked until [retryInMillis] have passed. */
    data class LockedOut(
        val retryInMillis: Long,
    ) : PinAttemptResult
}

/**
 * How long PIN entry locks after repeated wrong guesses. A 6-digit PIN has only a million
 * combinations, so without this a determined (or simply impatient) user could guess their way
 * past their own rules; the waits grow so a few typos cost little and brute force costs hours.
 */
object PinLockoutPolicy {
    /** Wrong PINs allowed before the first lockout. */
    const val FREE_ATTEMPTS = 5

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND

    /** Lockout after the [failedAttempts]-th wrong PIN in a row; 0 = no lockout yet. */
    fun lockoutMillis(failedAttempts: Int): Long =
        when {
            failedAttempts < FREE_ATTEMPTS -> 0
            failedAttempts == FREE_ATTEMPTS -> 30 * SECOND
            failedAttempts == FREE_ATTEMPTS + 1 -> 1 * MINUTE
            failedAttempts == FREE_ATTEMPTS + 2 -> 2 * MINUTE
            failedAttempts == FREE_ATTEMPTS + 3 -> 5 * MINUTE
            else -> 15 * MINUTE
        }
}
