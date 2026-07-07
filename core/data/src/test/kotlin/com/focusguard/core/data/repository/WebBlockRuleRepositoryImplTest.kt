package com.focusguard.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.focusguard.core.data.local.dao.WebBlockRuleDao
import com.focusguard.core.data.local.entity.toEntity
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A minimal in-memory [DataStore] fake — real datastore-preferences `edit`/updateData
 * machinery runs against it unmodified, only the backing storage is swapped out.
 */
private class WebBlockFakeDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class WebBlockRuleRepositoryImplTest {
    private val dao = mockk<WebBlockRuleDao>()
    private val dataStore = WebBlockFakeDataStore()
    private val repository = WebBlockRuleRepositoryImpl(dao, dataStore)

    @Test
    fun `getWebBlockRules maps entities to domain models`() =
        runTest {
            val rule = WebBlockRule(domain = "example.com")
            every { dao.getAll() } returns flowOf(listOf(rule.toEntity()))

            assertEquals(listOf(rule), repository.getWebBlockRules().first())
        }

    @Test
    fun `getEnabledDomains returns the DAO result as a set`() =
        runTest {
            every { dao.getEnabledDomains() } returns flowOf(listOf("a.com", "b.com"))

            assertEquals(setOf("a.com", "b.com"), repository.getEnabledDomains().first())
        }

    @Test
    fun `upsertWebBlockRule saves the mapped entity`() =
        runTest {
            val rule = WebBlockRule(domain = "example.com")
            coEvery { dao.upsert(rule.toEntity()) } returns Unit

            repository.upsertWebBlockRule(rule)

            coVerify(exactly = 1) { dao.upsert(rule.toEntity()) }
        }

    @Test
    fun `deleteWebBlockRule delegates to the DAO`() =
        runTest {
            coEvery { dao.deleteByDomain("example.com") } returns Unit

            repository.deleteWebBlockRule("example.com")

            coVerify(exactly = 1) { dao.deleteByDomain("example.com") }
        }

    @Test
    fun `getContentFilters is empty until filters are enabled`() =
        runTest {
            assertTrue(repository.getContentFilters().first().isEmpty())
        }

    @Test
    fun `setContentFilter then getContentFilters reflects the enabled filter`() =
        runTest {
            repository.setContentFilter(ContentFilter.REELS, enabled = true)

            assertEquals(setOf(ContentFilter.REELS), repository.getContentFilters().first())
        }

    @Test
    fun `setContentFilter enables TikTok FYP`() =
        runTest {
            repository.setContentFilter(ContentFilter.TIKTOK_FYP, enabled = true)

            assertEquals(setOf(ContentFilter.TIKTOK_FYP), repository.getContentFilters().first())
        }

    @Test
    fun `setContentFilter can disable a previously enabled filter`() =
        runTest {
            repository.setContentFilter(ContentFilter.SHORTS, enabled = true)
            repository.setContentFilter(ContentFilter.SHORTS, enabled = false)

            assertTrue(repository.getContentFilters().first().isEmpty())
        }
}
