package com.focusguard.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_sessions",
    indices = [Index(value = ["packageName", "date"])],
)
data class AppUsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    // LocalDate.toEpochDay()
    val date: Long,
    // Instant.toEpochMilli()
    val startTime: Long,
    // Instant.toEpochMilli()
    val endTime: Long,
)
