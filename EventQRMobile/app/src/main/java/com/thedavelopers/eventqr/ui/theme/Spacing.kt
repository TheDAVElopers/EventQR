package com.thedavelopers.eventqr.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EventQrSpacing(
    val default: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val mediumSmall: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val mediumLarge: Dp = 20.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val minTouchTarget: Dp = 48.dp,
    val screenHorizontalPadding: Dp = 16.dp,
    val screenVerticalPadding: Dp = 16.dp,
    val cardCornerRadius: Dp = 16.dp,
    val chipCornerRadius: Dp = 20.dp,
    val pillCornerRadius: Dp = 999.dp,
    val inputHeightMin: Dp = 48.dp,
    val buttonHeightMin: Dp = 48.dp,
)

val LocalSpacing = staticCompositionLocalOf { EventQrSpacing() }
