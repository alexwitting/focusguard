package com.focusguard.core.domain.model

data class WebBlockRule(
    val id: Long = 0,
    val domain: String,
    val blockSubdomains: Boolean = true,
    val enabled: Boolean = true,
)
