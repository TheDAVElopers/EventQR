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

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// V2 SEMANTIC ROLES - the redesign vocabulary (§3.2). Every one resolves a ui_* role in
// res/values/colors.xml, so a Phase 5 values-night override flips the whole app without a
// Compose change. These are composable-only properties: read them in a @Composable, never cache
// the resolved value in a top-level val.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

// Brand
val UiPrimary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_primary)
val UiOnPrimary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_primary)
val UiPrimaryPressed: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_primary_pressed)
val UiPrimaryContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_primary_container)
val UiOnPrimaryContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_primary_container)
val UiPrimaryMuted: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_primary_muted)
val UiPrimaryFocusRing: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_primary_focus_ring)
val UiPrimaryWash: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_primary_wash)

// Accent: lime confirms, it never decorates.
val UiAccent: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_accent)
val UiOnAccent: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_accent)
val UiAccentDeep: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_accent_deep)
val UiAccentContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_accent_container)

// Surfaces
val UiBackground: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_background)
val UiSurface: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_surface)
val UiSurfaceSunken: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_surface_sunken)
val UiSurfacePressed: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_surface_pressed)
val UiScrim: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_scrim)

// Lines and text: exactly two text greys.
val UiHairline: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_hairline)
val UiOutlineInteractive: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_outline_interactive)
val UiDisabledFill: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_disabled_fill)
val UiTextPrimary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_text_primary)
val UiTextSecondary: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_text_secondary)
val UiTextDisabled: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_text_disabled)
val UiTextOnDark: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_text_on_dark)
val UiTextOnDarkMuted: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_text_on_dark_muted)
val UiTextOnDarkFaint: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_text_on_dark_faint)
val UiDotNeutral: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_dot_neutral)

// Pass head / credential
val UiPassStub: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_pass_stub)
val UiOnDarkHairline: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_dark_hairline)

// Scanner
val UiScanPanel: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_scan_panel)
val UiScanBracket: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_scan_bracket)
val UiScanFrame: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_scan_frame)

// Semantic states
val UiSuccess: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_success)
val UiSuccessContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_success_container)
val UiOnSuccessContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_success_container)
val UiWarning: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_warning)
val UiWarningContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_warning_container)
val UiOnWarningContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_warning_container)
val UiError: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_error)
val UiErrorContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_error_container)
val UiOnErrorContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_error_container)
val UiInfo: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_info)
val UiInfoContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_info_container)
val UiOnInfoContainer: Color
    @Composable @ReadOnlyComposable get() = colorResource(R.color.ui_on_info_container)
