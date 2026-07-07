package com.focusguard.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.focusguard.core.data.local.dao.AppBlockRuleDao
import com.focusguard.core.data.local.dao.UsageDao
import com.focusguard.core.data.local.dao.WebBlockRuleDao
import com.focusguard.core.data.local.entity.AppBlockRuleEntity
import com.focusguard.core.data.local.entity.AppUsageSessionEntity
import com.focusguard.core.data.local.entity.BlockedAttemptEntity
import com.focusguard.core.data.local.entity.ExtensionRecordEntity
import com.focusguard.core.data.local.entity.WebBlockRuleEntity

@Database(
    entities = [
        AppBlockRuleEntity::class,
        WebBlockRuleEntity::class,
        AppUsageSessionEntity::class,
        BlockedAttemptEntity::class,
        ExtensionRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FocusGuardDatabase : RoomDatabase() {
    abstract fun appBlockRuleDao(): AppBlockRuleDao

    abstract fun webBlockRuleDao(): WebBlockRuleDao

    abstract fun usageDao(): UsageDao
}
