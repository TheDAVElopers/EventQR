package com.thedavelopers.eventqr.ui.theme

import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.thedavelopers.eventqr.R

/*
 * The redesign type scale (§3.5 of MyFiles/EventQR-Redesign-Plan.md).
 *
 * Two bundled families, fourteen roles, one job each. Archivo is display only and is never used
 * below 20sp. IBM Plex Sans is the text face. Numerals are tabular in every Numeric* role - the
 * tnum feature is baked into the style, so no screen can forget it.
 *
 * Every size, line height and tracking value is read from res/values/dimens_ui_shared.xml, so
 * XML (TextAppearance.EventQR.Type.*) and Compose resolve the same numbers. Nothing here is a
 * literal.
 */

/** Archivo 700/800. Display roles only; never below 20sp. */
val ArchivoFamily = FontFamily(
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
)

/** IBM Plex Sans 400/500/600. Every text role. */
val IbmPlexFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/** Reads a text-size token (a `<dimen>` in sp) as a Compose [TextUnit], unscaled. */
@Composable
@ReadOnlyComposable
fun spResource(@DimenRes id: Int): TextUnit {
    val value = TypedValue()
    LocalContext.current.resources.getValue(id, value, true)
    return TypedValue.complexToFloat(value.data).sp
}

/** A unitless float token as a letter spacing in em (0.16em = the overline tracking). */
@Composable
@ReadOnlyComposable
private fun trackingEm(@DimenRes id: Int): TextUnit =
    ResourcesCompat.getFloat(LocalContext.current.resources, id).em

/** The fourteen roles, resolved once per composition. */
@Immutable
data class EventQrType(
    val displayXl: TextStyle,
    val displayM: TextStyle,
    val headline: TextStyle,
    val titleL: TextStyle,
    val title: TextStyle,
    val bodyL: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    val micro: TextStyle,
    val numericL: TextStyle,
    val numericM: TextStyle,
    val numericS: TextStyle,
    /** Status badge label: 11sp / 600 / 0.06em, NOT all caps (§5.4). */
    val badge: TextStyle,
)

/** Builds the scale. Call inside a composition (it reads dimension resources). */
@Composable
@ReadOnlyComposable
fun eventQrType(): EventQrType = EventQrType(
    displayXl = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = spResource(R.dimen.ui_type_display_xl),
        lineHeight = spResource(R.dimen.ui_lh_display_xl),
        letterSpacing = trackingEm(R.dimen.ui_track_display_xl),
    ),
    displayM = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.ui_type_display_m),
        lineHeight = spResource(R.dimen.ui_lh_display_m),
        letterSpacing = trackingEm(R.dimen.ui_track_display_m),
    ),
    headline = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.ui_type_headline),
        lineHeight = spResource(R.dimen.ui_lh_headline),
        letterSpacing = trackingEm(R.dimen.ui_track_headline),
    ),
    titleL = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.ui_type_title_l),
        lineHeight = spResource(R.dimen.ui_lh_title_l),
        letterSpacing = trackingEm(R.dimen.ui_track_title_l),
    ),
    title = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.ui_type_title),
        lineHeight = spResource(R.dimen.ui_lh_title),
        letterSpacing = trackingEm(R.dimen.ui_track_title),
    ),
    bodyL = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.Normal,
        fontSize = spResource(R.dimen.ui_type_body_l),
        lineHeight = spResource(R.dimen.ui_lh_body_l),
        letterSpacing = trackingEm(R.dimen.ui_track_body),
    ),
    body = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.Normal,
        fontSize = spResource(R.dimen.ui_type_body),
        lineHeight = spResource(R.dimen.ui_lh_body),
        letterSpacing = trackingEm(R.dimen.ui_track_body),
    ),
    bodyS = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.Normal,
        fontSize = spResource(R.dimen.ui_type_body_s),
        lineHeight = spResource(R.dimen.ui_lh_body_s),
        letterSpacing = trackingEm(R.dimen.ui_track_body),
    ),
    label = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.Medium,
        fontSize = spResource(R.dimen.ui_type_label),
        lineHeight = spResource(R.dimen.ui_lh_label),
        letterSpacing = trackingEm(R.dimen.ui_track_label),
    ),
    overline = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.ui_type_overline),
        lineHeight = spResource(R.dimen.ui_lh_overline),
        letterSpacing = trackingEm(R.dimen.ui_track_overline),
    ),
    micro = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.Medium,
        fontSize = spResource(R.dimen.ui_type_micro),
        lineHeight = spResource(R.dimen.ui_lh_micro),
        letterSpacing = trackingEm(R.dimen.ui_track_micro),
    ),
    numericL = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.ui_type_numeric_l),
        lineHeight = spResource(R.dimen.ui_lh_numeric_l),
        letterSpacing = trackingEm(R.dimen.ui_track_numeric_l),
        fontFeatureSettings = "tnum",
    ),
    numericM = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.ui_type_numeric_m),
        lineHeight = spResource(R.dimen.ui_lh_numeric_m),
        letterSpacing = trackingEm(R.dimen.ui_track_numeric_m),
        fontFeatureSettings = "tnum",
    ),
    numericS = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.ui_type_numeric_s),
        lineHeight = spResource(R.dimen.ui_lh_numeric_s),
        letterSpacing = trackingEm(R.dimen.ui_track_numeric_s),
        fontFeatureSettings = "tnum",
    ),
    badge = TextStyle(
        fontFamily = IbmPlexFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.ui_type_overline),
        lineHeight = spResource(R.dimen.ui_lh_overline),
        letterSpacing = trackingEm(R.dimen.ui_track_badge),
        fontFeatureSettings = "tnum",
    ),
)

/**
 * Material 3 Typography mapped onto the fourteen roles, so a Material component that reads
 * `MaterialTheme.typography` inherits the new voice instead of Roboto.
 *
 * The mapping is deliberately lossy: M3 has no display_m or numeric_* slot, so those are read
 * through [LocalType] and never through MaterialTheme.
 */
@Composable
@ReadOnlyComposable
fun eventQrTypography(): Typography {
    val type = eventQrType()
    return Typography(
        displayLarge = type.displayXl,
        displayMedium = type.displayM,
        displaySmall = type.displayM,
        headlineLarge = type.headline,
        headlineMedium = type.headline,
        headlineSmall = type.titleL,
        titleLarge = type.title,
        titleMedium = type.title,
        titleSmall = type.label,
        bodyLarge = type.bodyL,
        bodyMedium = type.body,
        bodySmall = type.bodyS,
        labelLarge = type.label,
        labelMedium = type.label,
        labelSmall = type.micro,
    )
}

/** Kept for call sites that used the old top-level val; resolved per composition now. */
val EventQrTypography: Typography
    @Composable @ReadOnlyComposable get() = eventQrTypography()
