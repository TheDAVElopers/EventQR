package com.thedavelopers.eventqr.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EventQrSpacing(
    val default: Dp = 0.dp,
    val nano: Dp = 2.dp,
    val extraSmall: Dp = 4.dp,
    val micro: Dp = 6.dp,
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
    val cardCornerRadiusSmall: Dp = 12.dp,
    val chipCornerRadius: Dp = 20.dp,
    val pillCornerRadius: Dp = 999.dp,
    val inputHeightMin: Dp = 48.dp,
    val buttonHeightMin: Dp = 48.dp,
    val cardMinHeight: Dp = 96.dp,
    val listItemMinHeight: Dp = 72.dp,
    val cardElevation: Dp = 2.dp,
    val navElevation: Dp = 8.dp,
    val cardContentPadding: Dp = 16.dp,
    val cardContentGap: Dp = 14.dp,
    val badgeHorizontalPadding: Dp = 10.dp,
    val badgeContentPadding: Dp = 4.dp,
    val dateBadgeSize: Dp = 54.dp,
    val iconSizeSmall: Dp = 14.dp,
    val iconSizeMedium: Dp = 20.dp,
    val iconSizeLarge: Dp = 24.dp,
    val iconSizeXLarge: Dp = 36.dp,
    val iconContainerSmall: Dp = 36.dp,
    val iconContainerLarge: Dp = 72.dp,
    val brandLogoSize: Dp = 72.dp,
    val emptyStateVerticalPadding: Dp = 40.dp,
    val progressBarHeight: Dp = 6.dp,
    val progressBarCornerRadius: Dp = 3.dp,
    val chipBorderWidth: Dp = 1.dp,
)

val LocalSpacing = staticCompositionLocalOf { EventQrSpacing() }
