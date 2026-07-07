package com.focusguard.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_attempts",
    indices = [Index(value = ["packageName", "date"])],
)
data class BlockedAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    // LocalDate.toEpochDay()
    val date: Long,
    // Instant.toEpochMilli()
    val timestamp: Long,
)
