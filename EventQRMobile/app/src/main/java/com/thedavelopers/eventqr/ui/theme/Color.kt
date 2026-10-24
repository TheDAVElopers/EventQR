package com.thedavelopers.eventqr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.thedavelopers.eventqr.R

/*
 * Compose view of the colour ROLES in res/values/colors.xml (the single source of truth).
 *
 * Each name below resolves its Android colour resource at composition time, so a rebrand in
 * colors_palette.xml reaches Compose screens too. Because resolution needs a Context, these are
 * composable-only properties: read them inside a @Composable (or capture them there). Never copy the
 * resolved value into a top-level val.
 */

// Brand Core Colors
val BrandPrimary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.primary)
val BrandPrimaryDark: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.primary_dark)
val BrandPrimaryLight: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.primary_light)
val BrandPurple: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.secondary)
val BrandTertiary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.warning_dark)

// Neutrals
val PaperWhite: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.surface)
val SurfaceAlt: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.background)
val BackgroundLight: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.background)
val BorderLight: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.outline)
val OutlineVariant: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.outline)

// Text Colors
val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.text_primary)
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.text_secondary)
val TextMuted: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.text_hint)
val TextOnPrimary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_primary)
val TextOnPrimaryMuted: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_primary_muted)

// Semantic & Status Colors
val StatusActiveGreen: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.success)
val StatusActiveGreenBg: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.success_container)
val StatusActiveGreenText: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_success_container)

val StatusPendingAmberBg: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.warning_container)
val StatusPendingAmberText: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_warning_container)

val StatusRejectedRed: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.error)
val StatusRejectedRedBg: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.error_container)
val StatusRejectedRedText: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_error_container)

val StatusApprovedGreenBg: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.success_container)
val StatusApprovedGreenText: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_success_container)

val StatusCompletedGrayBg: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.badge_default_bg)
val StatusCompletedGrayText: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.badge_default_text)

val StatusRegisteredPurpleBg: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.secondary_container)
val StatusRegisteredPurpleText: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_secondary_container)

// Event Card Status Accents
val EventAccentUpcoming: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_warning_container)
val EventAccentUpcomingFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.warning_dark)
val EventAccentActive: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_success_container)
val EventAccentActiveFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.success_dark)
val EventAccentCompleted: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.badge_default_text)
val EventAccentCompletedFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.text_secondary)
val EventAccentPending: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_warning_container)
val EventAccentPendingFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.warning_dark)
val EventAccentApproved: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_success_container)
val EventAccentApprovedFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_success_container)
val EventAccentRegistered: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_secondary_container)
val EventAccentRegisteredFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.accent)
val EventAccentRejected: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.on_error_container)
val EventAccentRejectedFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.error)
