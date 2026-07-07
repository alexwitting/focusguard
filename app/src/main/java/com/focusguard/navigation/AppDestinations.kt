package com.focusguard.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
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
        TopLevelDestination(HomeRoute, Icons.Default.DateRange, "Home"),
        TopLevelDestination(BlockRoute, Icons.Default.Lock, "Block"),
        TopLevelDestination(SettingsRoute, Icons.Default.Settings, "Settings"),
    )
