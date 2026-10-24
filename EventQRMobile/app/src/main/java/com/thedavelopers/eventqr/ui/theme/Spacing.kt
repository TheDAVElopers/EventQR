package com.thedavelopers.eventqr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.R

/**
 * Compose view of the dimension tokens in res/values/dimens.xml (the single source of truth for sizes).
 *
 * There are no literal values here: build one with [eventQrSpacing] inside a composition. [EventQrTheme] and
 * [EventQrRowTheme] do that and provide it through [LocalSpacing].
 */
@Immutable
data class EventQrSpacing(
    val default: Dp = 0.dp,
    val nano: Dp,
    val extraSmall: Dp,
    val micro: Dp,
    val small: Dp,
    val mediumSmall: Dp,
    val medium: Dp,
    val mediumLarge: Dp,
    val large: Dp,
    val extraLarge: Dp,
    val huge: Dp,
    val minTouchTarget: Dp,
    val screenHorizontalPadding: Dp,
    val screenVerticalPadding: Dp,
    val cardCornerRadius: Dp,
    val cardCornerRadiusSmall: Dp,
    val chipCornerRadius: Dp,
    val pillCornerRadius: Dp,
    val inputHeightMin: Dp,
    val buttonHeightMin: Dp,
    val cardMinHeight: Dp,
    val listItemMinHeight: Dp,
    val cardElevation: Dp,
    val navElevation: Dp,
    val cardContentPadding: Dp,
    val cardContentGap: Dp,
    val badgeHorizontalPadding: Dp,
    val badgeContentPadding: Dp,
    val dateBadgeSize: Dp,
    val iconSizeSmall: Dp,
    val iconSizeMedium: Dp,
    val iconSizeLarge: Dp,
    val iconSizeXLarge: Dp,
    val iconContainerSmall: Dp,
    val iconContainerLarge: Dp,
    val brandLogoSize: Dp,
    val emptyStateVerticalPadding: Dp,
    val progressBarHeight: Dp,
    val progressBarCornerRadius: Dp,
    val chipBorderWidth: Dp,
)

/** The app's spacing, resolved from the `@dimen` tokens (value-for-value what the old constants were). */
@Composable
@ReadOnlyComposable
fun eventQrSpacing(): EventQrSpacing = EventQrSpacing(
    nano = dimensionResource(R.dimen.space_xxs),
    extraSmall = dimensionResource(R.dimen.space_xs),
    micro = dimensionResource(R.dimen.space_6),
    small = dimensionResource(R.dimen.space_sm),
    mediumSmall = dimensionResource(R.dimen.space_md),
    medium = dimensionResource(R.dimen.space_lg),
    mediumLarge = dimensionResource(R.dimen.space_20),
    large = dimensionResource(R.dimen.space_xl),
    extraLarge = dimensionResource(R.dimen.space_xxl),
    huge = dimensionResource(R.dimen.space_xxxl),
    minTouchTarget = dimensionResource(R.dimen.touch_target_min),
    screenHorizontalPadding = dimensionResource(R.dimen.screen_gutter),
    screenVerticalPadding = dimensionResource(R.dimen.screen_gutter),
    cardCornerRadius = dimensionResource(R.dimen.radius_card),
    cardCornerRadiusSmall = dimensionResource(R.dimen.radius_card_sm),
    chipCornerRadius = dimensionResource(R.dimen.radius_chip),
    pillCornerRadius = dimensionResource(R.dimen.radius_pill),
    inputHeightMin = dimensionResource(R.dimen.touch_target_min),
    buttonHeightMin = dimensionResource(R.dimen.touch_target_min),
    cardMinHeight = dimensionResource(R.dimen.card_min_height),
    listItemMinHeight = dimensionResource(R.dimen.list_item_min_height),
    cardElevation = dimensionResource(R.dimen.elevation_raised),
    navElevation = dimensionResource(R.dimen.elevation_overlay),
    cardContentPadding = dimensionResource(R.dimen.card_padding),
    cardContentGap = dimensionResource(R.dimen.space_14),
    badgeHorizontalPadding = dimensionResource(R.dimen.space_10),
    badgeContentPadding = dimensionResource(R.dimen.space_xs),
    dateBadgeSize = dimensionResource(R.dimen.control_height_54),
    iconSizeSmall = dimensionResource(R.dimen.icon_14),
    iconSizeMedium = dimensionResource(R.dimen.icon_md),
    iconSizeLarge = dimensionResource(R.dimen.icon_lg),
    iconSizeXLarge = dimensionResource(R.dimen.icon_xl),
    iconContainerSmall = dimensionResource(R.dimen.icon_tile_sm),
    iconContainerLarge = dimensionResource(R.dimen.tile_72),
    brandLogoSize = dimensionResource(R.dimen.tile_72),
    emptyStateVerticalPadding = dimensionResource(R.dimen.space_40),
    progressBarHeight = dimensionResource(R.dimen.progress_bar_height),
    progressBarCornerRadius = dimensionResource(R.dimen.radius_3),
    chipBorderWidth = dimensionResource(R.dimen.stroke_hairline),
)

/** Provided by [EventQrTheme] / [EventQrRowTheme]; reading it outside one is a programming error. */
val LocalSpacing = staticCompositionLocalOf<EventQrSpacing> {
    error("EventQrSpacing is not provided: wrap the content in EventQrTheme or EventQrRowTheme")
}
