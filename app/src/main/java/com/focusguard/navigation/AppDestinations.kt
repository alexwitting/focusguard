package com.focusguard.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data object HomeRoute

@Serializable data object BlockRoute

@Serializable data object SettingsRoute

data class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    val label: String,
)

val TOP_LEVEL_DESTINATIONS =
    listOf(
        TopLevelDestination(HomeRoute, Icons.Outlined.BarChart, "Today"),
        TopLevelDestination(BlockRoute, Icons.Outlined.Block, "Block"),
        TopLevelDestination(SettingsRoute, Icons.Outlined.Tune, "Settings"),
    )
