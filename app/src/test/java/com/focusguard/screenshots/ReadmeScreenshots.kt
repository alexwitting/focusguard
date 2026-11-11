package com.focusguard.screenshots

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusguard.core.domain.model.AppBlockRule
import com.focusguard.core.domain.model.AppCategory
import com.focusguard.core.domain.model.AppUsageSummary
import com.focusguard.core.domain.model.AppWithRule
import com.focusguard.core.domain.model.BlockingStrategy
import com.focusguard.core.domain.model.DailyTotal
import com.focusguard.core.domain.model.TimeWindow
import com.focusguard.core.ui.theme.FocusGuardTheme
import com.focusguard.feature.block.BlockContent
import com.focusguard.feature.block.BlockState
import com.focusguard.feature.block.BlockTab
import com.focusguard.feature.home.HomeContent
import com.focusguard.feature.home.HomeState
import com.focusguard.navigation.BlockRoute
import com.focusguard.navigation.FocusGuardNavigationBar
import com.focusguard.navigation.HomeRoute
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Renders the Home and Block screens with sample data and saves them as the README screenshots.
 * Skipped unless Gradle runs with `-PrecordScreenshots` (see app/build.gradle.kts). Uses a plain
 * [Application] since these composables need no Hilt or WorkManager.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class ReadmeScreenshots {
    private val outputDir = File(System.getProperty("focusguard.screenshotDir") ?: "build/screenshots")

    @Before
    fun onlyWhenRecording() {
        assumeTrue(System.getProperty("roborazzi.test.record") == "true")
    }

    @Test
    fun home() {
        captureRoboImage(File(outputDir, "home.png").path) {
            Screen(selectedRoute = HomeRoute) { modifier ->
                HomeContent(state = sampleHomeState(), onOpenSettings = {}, modifier = modifier)
            }
        }
    }

    @Test
    fun blockApps() {
        captureRoboImage(File(outputDir, "block-apps.png").path) {
            Screen(selectedRoute = BlockRoute) { modifier ->
                BlockContent(state = sampleBlockState(), onIntent = {}, onAppClick = {}, modifier = modifier)
            }
        }
    }

    @Composable
    private fun Screen(
        selectedRoute: Any,
        content: @Composable (Modifier) -> Unit,
    ) {
        FocusGuardTheme(darkTheme = false) {
            Scaffold(
                bottomBar = { FocusGuardNavigationBar(selectedRoute = selectedRoute, onSelect = {}) },
            ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
        }
    }

    // ── Sample data ──────────────────────────────────────────────────────────

    private fun sampleHomeState(): HomeState {
        // HomeContent highlights the bar whose date is today, so anchor the week to now.
        val today = LocalDate.now()
        val week = listOf(232, 198, 251, 210, 244, 228, 192)
        return HomeState(
            today = today,
            isLoading = false,
            blockedAttempts = 14,
            weeklyTotals = week.mapIndexed { i, minutes -> DailyTotal(today.minusDays((6 - i).toLong()), minutes) },
            dailyUsage =
                listOf(
                    usage("com.instagram.android", "Instagram", 45, limit = 45),
                    usage("com.google.android.youtube", "YouTube", 38, limit = 60),
                    usage("com.reddit.frontpage", "Reddit", 12, limit = 30),
                    usage("com.spotify.music", "Spotify", 52),
                    usage("com.android.chrome", "Chrome", 24),
                    usage("com.whatsapp", "WhatsApp", 15),
                    usage("com.google.android.apps.maps", "Maps", 6),
                ),
            weeklyAppBreakdown =
                listOf(
                    usage("com.google.android.youtube", "YouTube", 432),
                    usage("com.spotify.music", "Spotify", 380),
                    usage("com.instagram.android", "Instagram", 315),
                    usage("com.android.chrome", "Chrome", 168),
                ),
        )
    }

    private fun sampleBlockState(): BlockState {
        val weekdays = DayOfWeek.entries.toSet() - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        return BlockState(
            isLoading = false,
            selectedTab = BlockTab.APPS,
            isPinEnabled = true,
            apps =
                listOf(
                    app("com.instagram.android", "Instagram", AppCategory.SOCIAL, BlockingStrategy.TimeLimit(45)),
                    app("com.zhiliaoapp.musically", "TikTok", AppCategory.VIDEO, BlockingStrategy.AlwaysBlock),
                    app("com.google.android.youtube", "YouTube", AppCategory.VIDEO, BlockingStrategy.TimeLimit(60)),
                    app(
                        "com.reddit.frontpage",
                        "Reddit",
                        AppCategory.NEWS,
                        BlockingStrategy.ScheduledBlock(listOf(TimeWindow(17, 0, 22, 0, weekdays))),
                    ),
                    app("com.android.chrome", "Chrome", AppCategory.PRODUCTIVITY, null),
                    app("com.spotify.music", "Spotify", AppCategory.OTHER, null),
                    app("com.google.android.apps.maps", "Maps", AppCategory.PRODUCTIVITY, null),
                    app("com.whatsapp", "WhatsApp", AppCategory.SOCIAL, null),
                ),
            usageByPackage =
                mapOf(
                    "com.instagram.android" to 45,
                    "com.google.android.youtube" to 38,
                    "com.android.chrome" to 24,
                    "com.spotify.music" to 65,
                    "com.google.android.apps.maps" to 6,
                    "com.whatsapp" to 15,
                ),
        )
    }

    private fun usage(
        packageName: String,
        label: String,
        minutes: Int,
        limit: Int? = null,
    ) = AppUsageSummary(packageName = packageName, label = label, totalMinutes = minutes, limitMinutes = limit)

    private fun app(
        packageName: String,
        label: String,
        category: AppCategory,
        strategy: BlockingStrategy?,
    ) = AppWithRule(
        packageName = packageName,
        label = label,
        category = category,
        rule = strategy?.let { AppBlockRule(packageName = packageName, label = label, strategy = it) },
    )
}
