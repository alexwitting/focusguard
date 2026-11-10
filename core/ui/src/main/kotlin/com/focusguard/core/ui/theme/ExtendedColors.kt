package com.focusguard.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour roles Material 3's [androidx.compose.material3.ColorScheme] has no slot for.
 * Read via `MaterialTheme.extendedColors` inside [FocusGuardTheme].
 */
@Immutable
data class ExtendedColors(
    /** Progress toward a limit that is close (>= 80%) but not yet reached. */
    val warning: Color,
    /** Bars in a usage chart other than the highlighted (today) one. */
    val chartInactive: Color,
)

internal val LightExtendedColors =
    ExtendedColors(
        warning = WarningLight,
        chartInactive = ChartInactiveLight,
    )

internal val DarkExtendedColors =
    ExtendedColors(
        warning = WarningDark,
        chartInactive = ChartInactiveDark,
    )

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

@Suppress("UnusedReceiverParameter")
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
