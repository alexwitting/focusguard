package com.focusguard.core.data.repository

import android.content.pm.ApplicationInfo
import com.focusguard.core.data.DataTestEnvironment
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.TimeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** App rules saved to a real Room database and joined with Robolectric's installed apps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppBlockRuleRepositoryImplTest {
    private val env = DataTestEnvironment()
    private val repository = AppBlockRuleRepositoryImpl(env.db.appBlockRuleDao(), env.packageManager)

    @After
    fun tearDown() = env.close()

    private suspend fun apps() =
        repository
            .getAllAppsWithRules()
            .first()
            .filter { it.packageName.startsWith("com.example.") }

    @Test
    fun `installed user apps are listed by label with their rules, system apps left out`() =
        runTest {
            env.installApp("com.example.zebra", "zebra")
            env.installApp("com.example.alpha", "Alpha", category = ApplicationInfo.CATEGORY_SOCIAL)
            env.installApp("com.example.system", "System thing", system = true)
            val rule = AppBlockRule(packageName = "com.example.alpha", label = "Alpha", strategy = BlockingStrategy.TimeLimit(45))

            repository.upsertAppBlockRule(rule)

            val listed = apps()
            assertEquals(listOf("com.example.alpha", "com.example.zebra"), listed.map { it.packageName })
            assertEquals(rule, listed.first().rule)
            assertNull(listed.last().rule)
            assertEquals(AppCategory.SOCIAL, listed.first().category)
        }

    @Test
    fun `every strategy survives a save and reload, and replacing or removing a rule takes effect`() =
        runTest {
            val schedule =
                BlockingStrategy.ScheduledBlock(
                    listOf(TimeWindow(9, 0, 17, 30, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), TimeWindow(22, 0, 6, 0)),
                )
            val saved =
                listOf(
                    AppBlockRule(packageName = "com.a", label = "A", strategy = BlockingStrategy.AlwaysBlock),
                    AppBlockRule(packageName = "com.b", label = "B", strategy = BlockingStrategy.TimeLimit(20), enabled = false),
                    AppBlockRule(packageName = "com.c", label = "C", strategy = schedule),
                )
            saved.forEach { repository.upsertAppBlockRule(it) }
            assertEquals(saved.toSet(), repository.getAppBlockRules().first().toSet())

            repository.upsertAppBlockRule(saved[0].copy(strategy = BlockingStrategy.TimeLimit(5)))
            repository.deleteAppBlockRule("com.c")

            val after = repository.getAppBlockRules().first().associateBy { it.packageName }
            assertEquals(BlockingStrategy.TimeLimit(5), after.getValue("com.a").strategy)
            assertTrue("com.c" !in after)
        }

    @Test
    fun `app categories map onto the app's own groups`() =
        runTest {
            val categories =
                mapOf(
                    ApplicationInfo.CATEGORY_VIDEO to AppCategory.VIDEO,
                    ApplicationInfo.CATEGORY_AUDIO to AppCategory.VIDEO,
                    ApplicationInfo.CATEGORY_GAME to AppCategory.GAMES,
                    ApplicationInfo.CATEGORY_PRODUCTIVITY to AppCategory.PRODUCTIVITY,
                    ApplicationInfo.CATEGORY_NEWS to AppCategory.NEWS,
                    ApplicationInfo.CATEGORY_UNDEFINED to AppCategory.OTHER,
                )
            val entries = categories.entries.toList()
            entries.forEachIndexed { i, (category, _) -> env.installApp("com.example.app$i", "App $i", category = category) }

            val listed = apps().associate { it.packageName to it.category }

            entries.forEachIndexed { i, (_, expected) -> assertEquals(expected, listed["com.example.app$i"]) }
        }
}
