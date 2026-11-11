package com.focusguard.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focusguard.core.ui.R

// Bundled variable fonts (OFL, see core/ui/licenses/). Each weight pins the wght axis so it
// renders a real weight instead of synthetic bold.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    resId: Int,
    vararg weights: FontWeight,
) = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Headings and numerals. */
val SpaceGrotesk =
    variableFamily(
        R.font.space_grotesk_variable,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
    )

/** Body, labels, controls. */
val Inter =
    variableFamily(
        R.font.inter_variable,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
    )

private fun display(
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

private fun text(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

// Which style each element uses:
//   displayLarge   — hero number (today's screen time)
//   displayMedium  — stepper value in the app-rule sheet
//   displaySmall   — blocked-screen / onboarding headline
//   headlineLarge  — screen title ("Today", "Block", "Settings")
//   headlineSmall  — stat-tile numbers
//   titleLarge     — sheet / dialog title
//   titleMedium    — card section titles, list-row titles
//   labelMedium    — UPPERCASE section headers above grouped lists
val FocusGuardTypography =
    Typography(
        displayLarge = display(52, 56, tracking = -0.03),
        displayMedium = display(40, 44, tracking = -0.025),
        displaySmall = display(30, 36, tracking = -0.015),
        headlineLarge = display(32, 40, tracking = -0.015),
        headlineMedium = display(28, 34, tracking = -0.01),
        headlineSmall = display(24, 30),
        titleLarge = display(22, 28),
        titleMedium = text(15, 22, FontWeight.SemiBold),
        titleSmall = text(14, 20, FontWeight.SemiBold),
        bodyLarge = text(16, 24),
        bodyMedium = text(14, 20),
        bodySmall = text(13, 18),
        labelLarge = text(14, 20, FontWeight.SemiBold),
        labelMedium = text(12, 16, FontWeight.SemiBold, tracking = 0.05),
        labelSmall = text(11, 16, FontWeight.Medium, tracking = 0.02),
    )
