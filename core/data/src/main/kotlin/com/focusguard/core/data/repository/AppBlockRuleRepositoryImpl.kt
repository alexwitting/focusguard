package com.focusguard.core.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focusguard.core.data.local.dao.AppBlockRuleDao
import com.focusguard.core.data.local.entity.toDomain
import com.focusguard.core.data.local.entity.toEntity
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppBlockRuleRepositoryImpl
    @Inject
    constructor(
        private val dao: AppBlockRuleDao,
        private val packageManager: PackageManager,
    ) : BlockRuleRepository {
        override fun getAppBlockRules(): Flow<List<AppBlockRule>> = dao.getAll().map { entities -> entities.map { it.toDomain() } }

        override fun getAppBlockRule(packageName: String): Flow<AppBlockRule?> = dao.getByPackageName(packageName).map { it?.toDomain() }

        override fun getAllAppsWithRules(): Flow<List<AppWithRule>> =
            dao
                .getAll()
                .map { entities ->
                    val rulesByPackage = entities.associate { it.packageName to it.toDomain() }
                    packageManager
                        .getInstalledApplications(0)
                        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                        .map { appInfo ->
                            AppWithRule(
                                packageName = appInfo.packageName,
                                label = packageManager.getApplicationLabel(appInfo).toString(),
                                category = appInfo.category.toAppCategory(),
                                rule = rulesByPackage[appInfo.packageName],
                            )
                        }.sortedBy { it.label.lowercase() }
                }.flowOn(Dispatchers.IO)

        override suspend fun upsertAppBlockRule(rule: AppBlockRule) = dao.upsert(rule.toEntity())

        override suspend fun deleteAppBlockRule(packageName: String) = dao.deleteByPackageName(packageName)
    }

private fun Int.toAppCategory(): AppCategory =
    when (this) {
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> AppCategory.VIDEO
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
        else -> AppCategory.OTHER
    }
