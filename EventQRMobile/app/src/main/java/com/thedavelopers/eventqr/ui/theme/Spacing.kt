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
    chipCornerRadius = dimensionResource(R.dimen.ui_radius_button),
    pillCornerRadius = dimensionResource(R.dimen.radius_pill),
    inputHeightMin = dimensionResource(R.dimen.touch_target_min),
    buttonHeightMin = dimensionResource(R.dimen.touch_target_min),
    cardMinHeight = dimensionResource(R.dimen.card_min_height),
    listItemMinHeight = dimensionResource(R.dimen.list_item_min_height),
    cardElevation = dimensionResource(R.dimen.elevation_raised),
    navElevation = dimensionResource(R.dimen.elevation_overlay),
    cardContentPadding = dimensionResource(R.dimen.card_padding),
    cardContentGap = dimensionResource(R.dimen.space_14),
    badgeHorizontalPadding = dimensionResource(R.dimen.space_sm),
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

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// THE REDESIGN GRID (§4) - the values the new components read.
//
// Kept beside [EventQrSpacing] rather than inside it so the ~15 screens still on the legacy
// EventQrSpacing keep compiling unchanged; Phase 6 folds the two together.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

@Immutable
data class EventQrGrid(
    // Edges
    val gutter: Dp,
    val cardPadding: Dp,
    // Spacing scale (closed set of 11)
    val s2: Dp,
    val s4: Dp,
    val s8: Dp,
    val s12: Dp,
    val s16: Dp,
    val s20: Dp,
    val s24: Dp,
    val s32: Dp,
    val s40: Dp,
    val s48: Dp,
    val s64: Dp,
    // Radii
    val radiusButton: Dp,
    val radiusCard: Dp,
    val radiusSheet: Dp,
    val radiusBadge: Dp,
    // Controls
    val touchMin: Dp,
    val buttonHeight: Dp,
    val buttonHeightInline: Dp,
    val controlHeight: Dp,
    // Rows
    val rowHeight: Dp,
    val rowHeightValue: Dp,
    val rowHeightDate: Dp,
    val rowPaddingVertical: Dp,
    val rowStart: Dp,
    val rowEnd: Dp,
    val rowTile: Dp,
    val rowDateBlock: Dp,
    val dividerInset: Dp,
    val chevron: Dp,
    // Badge and chip
    val badgeHeight: Dp,
    val badgePadH: Dp,
    val badgeDot: Dp,
    val chipHeight: Dp,
    val chipPadH: Dp,
    val chipGap: Dp,
    // Navigation and chrome
    val navHeight: Dp,
    val navIcon: Dp,
    val navIndicatorWidth: Dp,
    val navIndicatorHeight: Dp,
    val topBarHeight: Dp,
    val topBarAction: Dp,
    val metricRowHeight: Dp,
    // Empty state and skeleton
    val emptyPlate: Dp,
    val emptyMaxWidth: Dp,
    val skeletonBarHeight: Dp,
    // Scanner
    val scanPanelWidth: Dp,
    val scanPanelHeight: Dp,
    val scanPreview: Dp,
    val scanBracketArm: Dp,
    val scanTorch: Dp,
    // Credential
    val credentialWidth: Dp,
    val credentialMinHeight: Dp,
    val qrPlate: Dp,
    val qrModule: Dp,
    val notch: Dp,
)

/** Resolves the grid from res/values/dimens_ui_shared.xml. */
@Composable
@ReadOnlyComposable
fun eventQrGrid(): EventQrGrid = EventQrGrid(
    gutter = dimensionResource(R.dimen.ui_gutter),
    cardPadding = dimensionResource(R.dimen.ui_card_padding),
    s2 = dimensionResource(R.dimen.space_2),
    s4 = dimensionResource(R.dimen.space_4),
    s8 = dimensionResource(R.dimen.space_8),
    s12 = dimensionResource(R.dimen.space_12),
    s16 = dimensionResource(R.dimen.space_16),
    s20 = dimensionResource(R.dimen.space_20),
    s24 = dimensionResource(R.dimen.space_24),
    s32 = dimensionResource(R.dimen.space_32),
    s40 = dimensionResource(R.dimen.space_40),
    s48 = dimensionResource(R.dimen.space_48),
    s64 = dimensionResource(R.dimen.space_64),
    radiusButton = dimensionResource(R.dimen.ui_radius_button),
    radiusCard = dimensionResource(R.dimen.ui_radius_card),
    radiusSheet = dimensionResource(R.dimen.ui_radius_sheet),
    radiusBadge = dimensionResource(R.dimen.ui_radius_badge),
    touchMin = dimensionResource(R.dimen.ui_touch_min),
    buttonHeight = dimensionResource(R.dimen.ui_button_height),
    buttonHeightInline = dimensionResource(R.dimen.ui_button_height_inline),
    controlHeight = dimensionResource(R.dimen.ui_control_height),
    rowHeight = dimensionResource(R.dimen.ui_row_height),
    rowHeightValue = dimensionResource(R.dimen.ui_row_height_value),
    rowHeightDate = dimensionResource(R.dimen.ui_row_height_date),
    rowPaddingVertical = dimensionResource(R.dimen.ui_row_padding_vertical),
    rowStart = dimensionResource(R.dimen.ui_row_start),
    rowEnd = dimensionResource(R.dimen.ui_row_end),
    rowTile = dimensionResource(R.dimen.ui_row_tile),
    rowDateBlock = dimensionResource(R.dimen.ui_row_date_block),
    dividerInset = dimensionResource(R.dimen.ui_divider_inset),
    chevron = dimensionResource(R.dimen.ui_chevron),
    badgeHeight = dimensionResource(R.dimen.ui_badge_height),
    badgePadH = dimensionResource(R.dimen.ui_badge_pad_h),
    badgeDot = dimensionResource(R.dimen.ui_badge_dot),
    chipHeight = dimensionResource(R.dimen.ui_chip_height),
    chipPadH = dimensionResource(R.dimen.ui_chip_pad_h),
    chipGap = dimensionResource(R.dimen.ui_chip_gap),
    navHeight = dimensionResource(R.dimen.ui_nav_height),
    navIcon = dimensionResource(R.dimen.ui_nav_icon),
    navIndicatorWidth = dimensionResource(R.dimen.ui_nav_indicator_width),
    navIndicatorHeight = dimensionResource(R.dimen.ui_nav_indicator_height),
    topBarHeight = dimensionResource(R.dimen.ui_top_bar_height),
    topBarAction = dimensionResource(R.dimen.ui_top_bar_action),
    metricRowHeight = dimensionResource(R.dimen.ui_metric_row_height),
    emptyPlate = dimensionResource(R.dimen.ui_empty_plate),
    emptyMaxWidth = dimensionResource(R.dimen.ui_empty_max_width),
    skeletonBarHeight = dimensionResource(R.dimen.ui_skeleton_bar_height),
    scanPanelWidth = dimensionResource(R.dimen.ui_scan_panel_width),
    scanPanelHeight = dimensionResource(R.dimen.ui_scan_panel_height),
    scanPreview = dimensionResource(R.dimen.ui_scan_preview),
    scanBracketArm = dimensionResource(R.dimen.ui_scan_bracket_arm),
    scanTorch = dimensionResource(R.dimen.ui_scan_torch),
    credentialWidth = dimensionResource(R.dimen.ui_credential_width),
    credentialMinHeight = dimensionResource(R.dimen.ui_credential_height),
    qrPlate = dimensionResource(R.dimen.ui_qr_plate),
    qrModule = dimensionResource(R.dimen.ui_qr_module),
    notch = dimensionResource(R.dimen.ui_notch),
)

/** Provided by [EventQrTheme] / [EventQrRowTheme]. */
val LocalGrid = staticCompositionLocalOf<EventQrGrid> {
    error("EventQrGrid is not provided: wrap the content in EventQrTheme or EventQrRowTheme")
}

