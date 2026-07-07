package com.focusguard.feature.block

import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule

data class BlockState(
    val selectedTab: BlockTab = BlockTab.APPS,
    val apps: List<AppWithRule> = emptyList(),
    val webRules: List<WebBlockRule> = emptyList(),
    val activeFilters: Set<ContentFilter> = emptySet(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isPinEnabled: Boolean = false,
    val pinRequired: Boolean = false,
    val pinInput: String = "",
    val pinError: String? = null,
    val usageByPackage: Map<String, Int> = emptyMap(),
) {
    val filteredApps: List<AppWithRule>
        get() =
            if (searchQuery.isBlank()) {
                apps
            } else {
                apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
            }

    val blockedApps: List<AppWithRule>
        get() = apps.filter { it.rule != null }

    val unblockedApps: List<AppWithRule>
        get() = apps.filter { it.rule == null }
}

enum class BlockTab(
    val label: String,
) {
    APPS("Apps"),
    WEBSITES("Websites"),
    CONTENT("Content"),
}

sealed interface BlockIntent {
    data class SelectTab(
        val tab: BlockTab,
    ) : BlockIntent

    data class SearchQueryChanged(
        val query: String,
    ) : BlockIntent

    data class SetAppStrategy(
        val packageName: String,
        val label: String,
        val strategy: BlockingStrategy,
    ) : BlockIntent

    data class RemoveAppRule(
        val packageName: String,
    ) : BlockIntent

    data class AddWebRule(
        val domain: String,
        val blockSubdomains: Boolean,
    ) : BlockIntent

    data class RemoveWebRule(
        val domain: String,
    ) : BlockIntent

    data class ToggleContentFilter(
        val filter: ContentFilter,
        val enabled: Boolean,
    ) : BlockIntent

    data class PinInputChanged(
        val value: String,
    ) : BlockIntent

    data object SubmitPin : BlockIntent

    data object DismissPinDialog : BlockIntent
}

sealed interface BlockEffect {
    data class ShowSnackbar(
        val message: String,
    ) : BlockEffect
}
