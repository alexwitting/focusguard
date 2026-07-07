package com.focusguard

data class BlockedState(
    val canRequestExtension: Boolean = false,
)

sealed interface BlockedEffect {
    data object ExtensionGranted : BlockedEffect
}
