package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import kotlinx.coroutines.flow.Flow

interface WebBlockRuleRepository {
    fun getWebBlockRules(): Flow<List<WebBlockRule>>

    fun getContentFilters(): Flow<Set<ContentFilter>>

    suspend fun upsertWebBlockRule(rule: WebBlockRule)

    suspend fun deleteWebBlockRule(domain: String)

    suspend fun setContentFilter(
        filter: ContentFilter,
        enabled: Boolean,
    )
}
