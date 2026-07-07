package com.focusguard.core.domain.model

data class AppBlockRule(
    val id: Long = 0,
    val packageName: String,
    val label: String,
    val strategy: BlockingStrategy,
    val enabled: Boolean = true,
)
