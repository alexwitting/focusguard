package com.focusguard.core.data.local.entity

import com.focusguard.core.data.local.dto.toBlockingStrategy
import com.focusguard.core.data.local.dto.toJson
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.WebBlockRule

internal fun AppBlockRuleEntity.toDomain() =
    AppBlockRule(
        packageName = packageName,
        label = label,
        strategy = strategyJson.toBlockingStrategy(),
        enabled = enabled,
    )

internal fun AppBlockRule.toEntity() =
    AppBlockRuleEntity(
        packageName = packageName,
        label = label,
        strategyJson = strategy.toJson(),
        enabled = enabled,
    )

internal fun WebBlockRuleEntity.toDomain() =
    WebBlockRule(
        domain = domain,
        blockSubdomains = blockSubdomains,
        enabled = enabled,
    )

internal fun WebBlockRule.toEntity() =
    WebBlockRuleEntity(
        domain = domain,
        blockSubdomains = blockSubdomains,
        enabled = enabled,
    )
