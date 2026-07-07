package com.focusguard.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.focusguard.core.data.local.entity.AppBlockRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppBlockRuleDao {
    @Query("SELECT * FROM app_block_rules ORDER BY label ASC")
    fun getAll(): Flow<List<AppBlockRuleEntity>>

    @Query("SELECT * FROM app_block_rules WHERE packageName = :packageName")
    fun getByPackageName(packageName: String): Flow<AppBlockRuleEntity?>

    @Upsert
    suspend fun upsert(entity: AppBlockRuleEntity)

    @Query("DELETE FROM app_block_rules WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
