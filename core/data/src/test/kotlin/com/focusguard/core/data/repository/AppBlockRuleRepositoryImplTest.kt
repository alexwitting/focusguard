package com.focusguard.core.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focusguard.core.data.local.dao.AppBlockRuleDao
import com.focusguard.core.data.local.entity.toEntity
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.BlockingStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppBlockRuleRepositoryImplTest {
    private val dao = mockk<AppBlockRuleDao>()
    private val packageManager = mockk<PackageManager>()
    private val repository = AppBlockRuleRepositoryImpl(dao, packageManager)

    private fun appInfo(
        pkg: String,
        flags: Int = 0,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ) = ApplicationInfo().apply {
        packageName = pkg
        this.flags = flags
        this.category = category
    }

    @Test
    fun `getAppBlockRules maps entities to domain models`() =
        runTest {
            val rule = AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.AlwaysBlock)
            every { dao.getAll() } returns flowOf(listOf(rule.toEntity()))

            val result = repository.getAppBlockRules().first()

            assertEquals(listOf(rule), result)
        }

    @Test
    fun `getAllAppsWithRules merges installed apps with existing rules and filters system apps`() =
        runTest {
            val rule = AppBlockRule(packageName = "com.rules", label = "Has Rule", strategy = BlockingStrategy.AlwaysBlock)
            every { dao.getAll() } returns flowOf(listOf(rule.toEntity()))
            every { packageManager.getInstalledApplications(0) } returns
                listOf(
                    appInfo("com.rules", category = ApplicationInfo.CATEGORY_SOCIAL),
                    appInfo("com.norule"),
                    appInfo("com.system", flags = ApplicationInfo.FLAG_SYSTEM),
                )
            every { packageManager.getApplicationLabel(any()) } answers {
                when (firstArg<ApplicationInfo>().packageName) {
                    "com.rules" -> "Has Rule"
                    "com.norule" -> "No Rule"
                    else -> "System App"
                }
            }

            val result = repository.getAllAppsWithRules().first()

            // Sorted by label, case-insensitive: "Has Rule" < "No Rule"
            assertEquals(listOf("com.rules", "com.norule"), result.map { it.packageName })
            assertEquals(rule, result.first { it.packageName == "com.rules" }.rule)
            assertNull(result.first { it.packageName == "com.norule" }.rule)
            assertEquals(AppCategory.SOCIAL, result.first { it.packageName == "com.rules" }.category)
        }

    @Test
    fun `getAllAppsWithRules maps every ApplicationInfo category to its AppCategory`() =
        runTest {
            every { dao.getAll() } returns flowOf(emptyList())
            every { packageManager.getInstalledApplications(0) } returns
                listOf(
                    appInfo("com.video", category = ApplicationInfo.CATEGORY_VIDEO),
                    appInfo("com.audio", category = ApplicationInfo.CATEGORY_AUDIO),
                    appInfo("com.game", category = ApplicationInfo.CATEGORY_GAME),
                    appInfo("com.productivity", category = ApplicationInfo.CATEGORY_PRODUCTIVITY),
                    appInfo("com.news", category = ApplicationInfo.CATEGORY_NEWS),
                    appInfo("com.other", category = ApplicationInfo.CATEGORY_UNDEFINED),
                )
            every { packageManager.getApplicationLabel(any()) } answers { firstArg<ApplicationInfo>().packageName }

            val result = repository.getAllAppsWithRules().first().associateBy { it.packageName }

            assertEquals(AppCategory.VIDEO, result.getValue("com.video").category)
            assertEquals(AppCategory.VIDEO, result.getValue("com.audio").category)
            assertEquals(AppCategory.GAMES, result.getValue("com.game").category)
            assertEquals(AppCategory.PRODUCTIVITY, result.getValue("com.productivity").category)
            assertEquals(AppCategory.NEWS, result.getValue("com.news").category)
            assertEquals(AppCategory.OTHER, result.getValue("com.other").category)
        }

    @Test
    fun `upsertAppBlockRule saves the mapped entity`() =
        runTest {
            val rule = AppBlockRule(packageName = "com.app", label = "App", strategy = BlockingStrategy.AlwaysBlock)
            coEvery { dao.upsert(any()) } returns Unit

            repository.upsertAppBlockRule(rule)

            coVerify(exactly = 1) { dao.upsert(rule.toEntity()) }
        }

    @Test
    fun `deleteAppBlockRule delegates to the DAO`() =
        runTest {
            coEvery { dao.deleteByPackageName("com.app") } returns Unit

            repository.deleteAppBlockRule("com.app")

            coVerify(exactly = 1) { dao.deleteByPackageName("com.app") }
        }
}
