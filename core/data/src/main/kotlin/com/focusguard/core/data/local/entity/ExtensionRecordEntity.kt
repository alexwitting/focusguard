package com.focusguard.core.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "extension_records",
    primaryKeys = ["packageName", "date"],
)
data class ExtensionRecordEntity(
    val packageName: String,
    // LocalDate.toEpochDay()
    val date: Long,
)
