package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.TimeWindow
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SettingsRepository {
    fun isGracePeriodEnabled(): Flow<Boolean>

    suspend fun setGracePeriodEnabled(enabled: Boolean)

    fun hasTakenExtensionToday(
        packageName: String,
        date: LocalDate,
    ): Flow<Boolean>

    suspend fun recordExtensionTaken(
        packageName: String,
        date: LocalDate,
    )

    fun isSyncEnabled(): Flow<Boolean>

    suspend fun setSyncEnabled(enabled: Boolean)

    fun getBedtime(): Flow<TimeWindow?>

    suspend fun setBedtime(window: TimeWindow?)

    fun isOnboardingComplete(): Flow<Boolean>

    suspend fun setOnboardingComplete()

    fun isWebContentBlockingEnabled(): Flow<Boolean>

    // Enable-only by design — once on, this cannot be turned back off from within the app.
    suspend fun enableWebContentBlocking()
}
