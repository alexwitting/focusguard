package com.focusguard.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.focusguard.core.data.local.entity.WebBlockRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebBlockRuleDao {
    @Query("SELECT * FROM web_block_rules ORDER BY domain ASC")
    fun getAll(): Flow<List<WebBlockRuleEntity>>

    @Query("SELECT domain FROM web_block_rules WHERE enabled = 1")
    fun getEnabledDomains(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entity: WebBlockRuleEntity)

    @Query("DELETE FROM web_block_rules WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)
}
