package com.focusguard.core.testing

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.repository.BlockRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBlockRuleRepository : BlockRuleRepository {
    private val _rules = MutableStateFlow<Map<String, AppBlockRule>>(emptyMap())
    private val installed = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Installed apps as package to label; apps with a rule are listed even if not installed. */
    fun setInstalledApps(vararg apps: Pair<String, String>) {
        installed.value = apps.toMap()
    }

    fun setRules(vararg rules: AppBlockRule) {
        _rules.value = rules.associateBy { it.packageName }
    }

    override fun getAppBlockRules(): Flow<List<AppBlockRule>> = _rules.map { it.values.toList() }

    /** The stored rule for [packageName], for assertions. */
    fun getAppBlockRule(packageName: String): Flow<AppBlockRule?> = _rules.map { it[packageName] }

    override fun getAllAppsWithRules(): Flow<List<AppWithRule>> =
        combine(installed, _rules) { apps, rules ->
            val labels = apps + rules.mapValues { it.value.label }
            labels
                .map { (pkg, label) -> AppWithRule(packageName = pkg, label = label, category = AppCategory.OTHER, rule = rules[pkg]) }
                .sortedBy { it.label.lowercase() }
        }

    override suspend fun upsertAppBlockRule(rule: AppBlockRule) {
        _rules.update { it + (rule.packageName to rule) }
    }

    override suspend fun deleteAppBlockRule(packageName: String) {
        _rules.update { it - packageName }
    }
}
