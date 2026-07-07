package com.focusguard.core.testing

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBlockRuleRepository : BlockRuleRepository {
    private val _rules = MutableStateFlow<Map<String, AppBlockRule>>(emptyMap())

    fun setRules(vararg rules: AppBlockRule) {
        _rules.value = rules.associateBy { it.packageName }
    }

    override fun getAppBlockRules(): Flow<List<AppBlockRule>> = _rules.map { it.values.toList() }

    override fun getAppBlockRule(packageName: String): Flow<AppBlockRule?> = _rules.map { it[packageName] }

    override fun getAllAppsWithRules(): Flow<List<AppWithRule>> =
        _rules.map { rules ->
            rules.values.map { rule ->
                AppWithRule(
                    packageName = rule.packageName,
                    label = rule.label,
                    category = AppCategory.OTHER,
                    rule = rule,
                )
            }
        }

    override suspend fun upsertAppBlockRule(rule: AppBlockRule) {
        _rules.update { it + (rule.packageName to rule) }
    }

    override suspend fun deleteAppBlockRule(packageName: String) {
        _rules.update { it - packageName }
    }
}
