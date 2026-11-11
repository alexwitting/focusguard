package com.focusguard.core.testing

import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import com.focusguard.core.domain.repository.WebBlockRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeWebBlockRuleRepository : WebBlockRuleRepository {
    private val _rules = MutableStateFlow<Map<String, WebBlockRule>>(emptyMap())
    private val _contentFilters = MutableStateFlow<Set<ContentFilter>>(emptySet())

    fun setRules(vararg rules: WebBlockRule) {
        _rules.value = rules.associateBy { it.domain }
    }

    override fun getWebBlockRules(): Flow<List<WebBlockRule>> = _rules.map { it.values.toList() }

    override fun getContentFilters(): Flow<Set<ContentFilter>> = _contentFilters

    override suspend fun upsertWebBlockRule(rule: WebBlockRule) {
        _rules.update { it + (rule.domain to rule) }
    }

    override suspend fun deleteWebBlockRule(domain: String) {
        _rules.update { it - domain }
    }

    override suspend fun setContentFilter(
        filter: ContentFilter,
        enabled: Boolean,
    ) {
        _contentFilters.update { current ->
            if (enabled) current + filter else current - filter
        }
    }
}
