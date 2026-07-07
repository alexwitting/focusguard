package com.focusguard.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.focusguard.core.data.local.dao.WebBlockRuleDao
import com.focusguard.core.data.local.entity.toDomain
import com.focusguard.core.data.local.entity.toEntity
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WebBlockRuleRepositoryImpl
    @Inject
    constructor(
        private val dao: WebBlockRuleDao,
        private val dataStore: DataStore<Preferences>,
    ) : WebBlockRuleRepository {
        override fun getWebBlockRules(): Flow<List<WebBlockRule>> = dao.getAll().map { entities -> entities.map { it.toDomain() } }

        override fun getEnabledDomains(): Flow<Set<String>> = dao.getEnabledDomains().map { it.toSet() }

        override fun getContentFilters(): Flow<Set<ContentFilter>> =
            dataStore.data.map { prefs ->
                buildSet {
                    if (prefs[Keys.REELS] == true) add(ContentFilter.REELS)
                    if (prefs[Keys.SHORTS] == true) add(ContentFilter.SHORTS)
                    if (prefs[Keys.TIKTOK_FYP] == true) add(ContentFilter.TIKTOK_FYP)
                }
            }

        override suspend fun upsertWebBlockRule(rule: WebBlockRule) = dao.upsert(rule.toEntity())

        override suspend fun deleteWebBlockRule(domain: String) = dao.deleteByDomain(domain)

        override suspend fun setContentFilter(
            filter: ContentFilter,
            enabled: Boolean,
        ) {
            dataStore.edit { prefs ->
                prefs[filter.toKey()] = enabled
            }
        }

        private fun ContentFilter.toKey() =
            when (this) {
                ContentFilter.REELS -> Keys.REELS
                ContentFilter.SHORTS -> Keys.SHORTS
                ContentFilter.TIKTOK_FYP -> Keys.TIKTOK_FYP
            }

        private object Keys {
            val REELS = booleanPreferencesKey("content_filter_reels")
            val SHORTS = booleanPreferencesKey("content_filter_shorts")
            val TIKTOK_FYP = booleanPreferencesKey("content_filter_tiktok_fyp")
        }
    }
