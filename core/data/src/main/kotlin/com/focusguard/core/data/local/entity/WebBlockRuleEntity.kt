package com.focusguard.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_block_rules")
data class WebBlockRuleEntity(
    @PrimaryKey val domain: String,
    val blockSubdomains: Boolean,
    val enabled: Boolean,
)
