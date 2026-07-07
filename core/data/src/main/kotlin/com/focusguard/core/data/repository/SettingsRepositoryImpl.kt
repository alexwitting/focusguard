package com.focusguard.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.focusguard.core.data.local.dao.UsageDao
import com.focusguard.core.data.local.dto.toJson
import com.focusguard.core.data.local.dto.toTimeWindow
import com.focusguard.core.data.local.entity.ExtensionRecordEntity
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val usageDao: UsageDao,
    ) : SettingsRepository {
        override fun isGracePeriodEnabled(): Flow<Boolean> = dataStore.data.map { it[Keys.GRACE_PERIOD_ENABLED] ?: false }

        override suspend fun setGracePeriodEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.GRACE_PERIOD_ENABLED] = enabled }
        }

        override fun hasTakenExtensionToday(
            packageName: String,
            date: LocalDate,
        ): Flow<Boolean> = usageDao.countExtension(packageName, date.toEpochDay()).map { it > 0 }

        override suspend fun recordExtensionTaken(
            packageName: String,
            date: LocalDate,
        ) {
            usageDao.recordExtension(ExtensionRecordEntity(packageName, date.toEpochDay()))
        }

        override fun isSyncEnabled(): Flow<Boolean> = dataStore.data.map { it[Keys.SYNC_ENABLED] ?: false }

        override suspend fun setSyncEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.SYNC_ENABLED] = enabled }
        }

        override fun getBedtime(): Flow<TimeWindow?> =
            dataStore.data.map { prefs ->
                prefs[Keys.BEDTIME_JSON]?.toTimeWindow()
            }

        override suspend fun setBedtime(window: TimeWindow?) {
            dataStore.edit { prefs ->
                if (window == null) {
                    prefs.remove(Keys.BEDTIME_JSON)
                } else {
                    prefs[Keys.BEDTIME_JSON] = window.toJson()
                }
            }
        }

        override fun isOnboardingComplete(): Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

        override suspend fun setOnboardingComplete() {
            dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = true }
        }

        private object Keys {
            val GRACE_PERIOD_ENABLED = booleanPreferencesKey("grace_period_enabled")
            val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
            val BEDTIME_JSON = stringPreferencesKey("bedtime_json")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        }
    }
