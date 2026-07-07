package com.focusguard.core.domain.model

data class AppWithRule(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val rule: AppBlockRule?,
)

enum class AppCategory {
    SOCIAL,
    VIDEO,
    GAMES,
    PRODUCTIVITY,
    NEWS,
    SHOPPING,
    OTHER,
}
