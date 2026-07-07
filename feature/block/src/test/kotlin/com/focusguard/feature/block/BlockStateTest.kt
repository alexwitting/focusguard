package com.focusguard.feature.block

import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.BlockingStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockStateTest {
    private val ruled =
        AppWithRule(
            "com.ruled",
            "Ruled App",
            AppCategory.SOCIAL,
            AppBlockRule(packageName = "com.ruled", label = "Ruled App", strategy = BlockingStrategy.AlwaysBlock),
        )
    private val unruled = AppWithRule("com.free", "Free App", AppCategory.OTHER, rule = null)
    private val state = BlockState(apps = listOf(ruled, unruled))

    @Test
    fun `filteredApps returns every app when the search query is blank`() {
        assertEquals(listOf(ruled, unruled), state.copy(searchQuery = "").filteredApps)
    }

    @Test
    fun `filteredApps matches case-insensitively against the label`() {
        assertEquals(listOf(ruled), state.copy(searchQuery = "ruled").filteredApps)
    }

    @Test
    fun `filteredApps returns nothing when no label matches`() {
        assertEquals(emptyList<AppWithRule>(), state.copy(searchQuery = "nonexistent").filteredApps)
    }

    @Test
    fun `blockedApps returns only apps with a rule`() {
        assertEquals(listOf(ruled), state.blockedApps)
    }

    @Test
    fun `unblockedApps returns only apps without a rule`() {
        assertEquals(listOf(unruled), state.unblockedApps)
    }
}
