package com.focusguard.core.domain.model

sealed class BlockDecision {
    data object Allow : BlockDecision()

    data class Block(
        val reason: BlockReason,
    ) : BlockDecision()
}

enum class BlockReason {
    ALWAYS_BLOCKED,
    TIME_LIMIT_REACHED,
    OUTSIDE_ALLOWED_WINDOW,
    CONTENT_FILTER,
    WEBSITE_BLOCKED,

    /** Inside the user's bedtime window — applies to every app that has a rule. */
    BEDTIME,
}
