package com.focusguard.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_block_rules")
data class AppBlockRuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val strategyJson: String,
    val enabled: Boolean,
)
