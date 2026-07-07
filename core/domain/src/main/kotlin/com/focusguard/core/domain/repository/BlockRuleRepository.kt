package com.focusguard.core.domain.repository

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppWithRule
import kotlinx.coroutines.flow.Flow

interface BlockRuleRepository {
    fun getAppBlockRules(): Flow<List<AppBlockRule>>

    fun getAppBlockRule(packageName: String): Flow<AppBlockRule?>

    fun getAllAppsWithRules(): Flow<List<AppWithRule>>

    suspend fun upsertAppBlockRule(rule: AppBlockRule)

    suspend fun deleteAppBlockRule(packageName: String)
}
