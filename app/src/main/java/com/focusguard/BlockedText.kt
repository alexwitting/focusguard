package com.focusguard

import com.focusguard.core.domain.model.BlockReason

/** The block screen's headline and the line under it. */
internal data class BlockedText(
    val headline: String,
    val body: String,
)

/** Text for a block of [label] (an app or site name), or of "this app" when the name is unknown. */
internal fun blockedText(
    reason: BlockReason,
    label: String?,
): BlockedText {
    val name = label ?: "this app"
    val sentenceName = label ?: "This app"
    return when (reason) {
        BlockReason.TIME_LIMIT_REACHED -> BlockedText("You've used today's limit for $name", "The limit resets at midnight.")
        BlockReason.ALWAYS_BLOCKED -> BlockedText("$sentenceName is blocked", "You can change this in your block list.")
        BlockReason.OUTSIDE_ALLOWED_WINDOW -> BlockedText("$sentenceName is blocked right now", "It's outside the hours you allowed.")
        BlockReason.CONTENT_FILTER -> BlockedText("This feed is blocked", "The rest of $name still works.")
        BlockReason.WEBSITE_BLOCKED -> BlockedText("This website is blocked", "It's on your block list.")
        BlockReason.BEDTIME -> BlockedText("It's bedtime", "$sentenceName is blocked until bedtime ends.")
    }
}
