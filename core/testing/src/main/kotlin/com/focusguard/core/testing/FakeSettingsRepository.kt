package com.focusguard.core.testing

import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeSettingsRepository : SettingsRepository {
    private val _gracePeriodEnabled = MutableStateFlow(false)
    private val _syncEnabled = MutableStateFlow(false)
    private val _bedtime = MutableStateFlow<TimeWindow?>(null)
    private val _extensions = MutableStateFlow<Set<Pair<String, LocalDate>>>(emptySet())
    private val _onboardingComplete = MutableStateFlow(false)
    private val _webContentBlockingEnabled = MutableStateFlow(false)

    override fun isGracePeriodEnabled(): Flow<Boolean> = _gracePeriodEnabled

    override suspend fun setGracePeriodEnabled(enabled: Boolean) {
        _gracePeriodEnabled.value = enabled
    }

    override fun hasTakenExtensionToday(
        packageName: String,
        date: LocalDate,
    ): Flow<Boolean> = _extensions.map { (packageName to date) in it }

    override suspend fun recordExtensionTaken(
        packageName: String,
        date: LocalDate,
    ) {
        _extensions.value = _extensions.value + (packageName to date)
    }

    override fun isSyncEnabled(): Flow<Boolean> = _syncEnabled

    override suspend fun setSyncEnabled(enabled: Boolean) {
        _syncEnabled.value = enabled
    }

    override fun getBedtime(): Flow<TimeWindow?> = _bedtime

    override suspend fun setBedtime(window: TimeWindow?) {
        _bedtime.value = window
    }

    override fun isOnboardingComplete(): Flow<Boolean> = _onboardingComplete

    override suspend fun setOnboardingComplete() {
        _onboardingComplete.value = true
    }

    override fun isWebContentBlockingEnabled(): Flow<Boolean> = _webContentBlockingEnabled

    override suspend fun enableWebContentBlocking() {
        _webContentBlockingEnabled.value = true
    }
}
