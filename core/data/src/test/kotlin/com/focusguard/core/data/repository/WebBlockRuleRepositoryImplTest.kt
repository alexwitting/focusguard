package com.focusguard.core.data.repository

import com.focusguard.core.data.DataTestEnvironment
import com.focusguard.core.domain.model.ContentFilter
import com.focusguard.core.domain.model.WebBlockRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Website rules in a real Room database and content filters in a real DataStore file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebBlockRuleRepositoryImplTest {
    private val env = DataTestEnvironment()
    private val repository = WebBlockRuleRepositoryImpl(env.db.webBlockRuleDao(), env.dataStore)

    @After
    fun tearDown() = env.close()

    @Test
    fun `websites are saved, listed by domain, updated and removed`() =
        runTest {
            repository.upsertWebBlockRule(WebBlockRule(domain = "youtube.com"))
            repository.upsertWebBlockRule(WebBlockRule(domain = "instagram.com", blockSubdomains = false))
            assertEquals(listOf("instagram.com", "youtube.com"), repository.getWebBlockRules().first().map { it.domain })

            repository.upsertWebBlockRule(WebBlockRule(domain = "youtube.com", enabled = false))
            repository.deleteWebBlockRule("instagram.com")

            assertEquals(listOf(WebBlockRule(domain = "youtube.com", enabled = false)), repository.getWebBlockRules().first())
        }

    @Test
    fun `content filters start off and each turns on and off independently`() =
        runTest {
            assertTrue(repository.getContentFilters().first().isEmpty())

            ContentFilter.entries.forEach { repository.setContentFilter(it, true) }
            assertEquals(ContentFilter.entries.toSet(), repository.getContentFilters().first())

            repository.setContentFilter(ContentFilter.SHORTS, false)
            assertEquals(setOf(ContentFilter.REELS, ContentFilter.TIKTOK_FYP), repository.getContentFilters().first())
        }
}
