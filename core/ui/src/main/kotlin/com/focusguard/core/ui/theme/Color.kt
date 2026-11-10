package com.focusguard.core.ui.theme

import androidx.compose.ui.graphics.Color

// "Quiet Focus" palette — warm paper ground, ink text, one indigo accent.
// Role mapping (keep to these so screens stay consistent):
//   primary    — the single accent: selected states, primary actions, today's bar
//   secondary  — "all good" / protected / granted status (sage green)
//   tertiary   — "limit reached" / attention (clay); near-limit uses ExtendedColors.warning
//   surfaceContainerLowest — card fill (white on the paper background)
//   outlineVariant         — card border and list dividers

// Light scheme
val PrimaryLight = Color(0xFF4F5BD5) // indigo accent
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE6E8FB)
val OnPrimaryContainerLight = Color(0xFF1E2675)
val SecondaryLight = Color(0xFF2F6E52) // sage — healthy status
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE3F1EA)
val OnSecondaryContainerLight = Color(0xFF123B29)
val TertiaryLight = Color(0xFFB4532A) // clay — limit reached
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFBEBD9)
val OnTertiaryContainerLight = Color(0xFF4A220A)
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
val BackgroundLight = Color(0xFFF6F5F1) // warm paper
val OnBackgroundLight = Color(0xFF16181D) // ink
val SurfaceLight = Color(0xFFF6F5F1)
val OnSurfaceLight = Color(0xFF16181D)
val SurfaceVariantLight = Color(0xFFECEAE3)
val OnSurfaceVariantLight = Color(0xFF5E6270) // muted text, 5.6:1 on paper
val OutlineLight = Color(0xFF8A8D98)
val OutlineVariantLight = Color(0xFFE7E5DE)
val InverseSurfaceLight = Color(0xFF2B2D33)
val InverseOnSurfaceLight = Color(0xFFF2F1EC)
val InversePrimaryLight = Color(0xFFBCC2FF)
val SurfaceTintLight = Color(0xFF4F5BD5)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF) // cards
val SurfaceContainerLowLight = Color(0xFFFBFAF7)
val SurfaceContainerLight = Color(0xFFF1EFEA)
val SurfaceContainerHighLight = Color(0xFFECEAE3) // segmented-control track, stat tiles
val SurfaceContainerHighestLight = Color(0xFFE5E3DB)

// Dark scheme — also the look of the blocked interstitial
val PrimaryDark = Color(0xFFBCC2FF)
val OnPrimaryDark = Color(0xFF1B2575)
val PrimaryContainerDark = Color(0xFF3A45B0)
val OnPrimaryContainerDark = Color(0xFFE6E8FB)
val SecondaryDark = Color(0xFF8FD1B0)
val OnSecondaryDark = Color(0xFF003822)
val SecondaryContainerDark = Color(0xFF1F5E43)
val OnSecondaryContainerDark = Color(0xFFC4E3D3)
val TertiaryDark = Color(0xFFF2B48C)
val OnTertiaryDark = Color(0xFF4A220A)
val TertiaryContainerDark = Color(0xFF7A3A12)
val OnTertiaryContainerDark = Color(0xFFFBEBD9)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
val BackgroundDark = Color(0xFF16181D) // ink
val OnBackgroundDark = Color(0xFFF2F1EC)
val SurfaceDark = Color(0xFF16181D)
val OnSurfaceDark = Color(0xFFF2F1EC)
val SurfaceVariantDark = Color(0xFF2C2F38)
val OnSurfaceVariantDark = Color(0xFFA9ACB6) // muted text, 7.9:1 on ink
val OutlineDark = Color(0xFF737782)
val OutlineVariantDark = Color(0xFF353946)
val InverseSurfaceDark = Color(0xFFE8E6E0)
val InverseOnSurfaceDark = Color(0xFF2B2D33)
val InversePrimaryDark = Color(0xFF4F5BD5)
val SurfaceTintDark = Color(0xFFBCC2FF)
val SurfaceContainerLowestDark = Color(0xFF111216)
val SurfaceContainerLowDark = Color(0xFF1B1D23) // cards
val SurfaceContainerDark = Color(0xFF1F2128)
val SurfaceContainerHighDark = Color(0xFF262931)
val SurfaceContainerHighestDark = Color(0xFF2E313A)

// Extended (non-M3) roles — see ExtendedColors.kt
val WarningLight = Color(0xFFC98A1B) // near-limit progress (>= 80%)
val WarningDark = Color(0xFFE6B45A)
val ChartInactiveLight = Color(0xFFDDDAD1) // non-highlighted chart bars
val ChartInactiveDark = Color(0xFF353946)
