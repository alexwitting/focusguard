package com.focusguard.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// extraSmall — progress bars, small chips
// small      — app icon tiles, status chips, segmented-control segments
// medium     — inputs, stat tiles, segmented-control track
// large      — cards and grouped lists
// extraLarge — bottom sheets, dialogs
val FocusGuardShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
