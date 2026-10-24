package com.thedavelopers.eventqr.ui.theme

import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.thedavelopers.eventqr.R

/**
 * Reads a text-size token (a `<dimen>` in sp, e.g. `type_headline_l`) as a Compose [TextUnit]. The number in dimens.xml is
 * returned unscaled, so Compose applies the user's font scale exactly once, as it does for a literal `28.sp`.
 */
@Composable
@ReadOnlyComposable
fun spResource(@DimenRes id: Int): TextUnit {
    val value = TypedValue()
    LocalContext.current.resources.getValue(id, value, true)
    return TypedValue.complexToFloat(value.data).sp
}

/** A unitless float token (`<item type="dimen" format="float">`) as a Compose letter spacing in sp. */
@Composable
@ReadOnlyComposable
private fun trackingResource(@DimenRes id: Int): TextUnit =
    ResourcesCompat.getFloat(LocalContext.current.resources, id).sp

/** Material 3 typography built from the `type_*`, `line_height_*` and `tracking_compose_*` tokens in dimens.xml. */
@Composable
@ReadOnlyComposable
fun eventQrTypography(): Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.type_headline_l),
        lineHeight = spResource(R.dimen.line_height_36),
        letterSpacing = trackingResource(R.dimen.tracking_compose_tight),
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.type_24),
        lineHeight = spResource(R.dimen.line_height_32),
        letterSpacing = trackingResource(R.dimen.tracking_compose_tight_sm),
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = spResource(R.dimen.type_title_large),
        lineHeight = spResource(R.dimen.line_height_28),
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.type_title),
        lineHeight = spResource(R.dimen.line_height_26),
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.type_body_large),
        lineHeight = spResource(R.dimen.line_height_24),
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = spResource(R.dimen.type_body_medium),
        lineHeight = spResource(R.dimen.line_height_20),
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = spResource(R.dimen.type_body_large),
        lineHeight = spResource(R.dimen.line_height_24),
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = spResource(R.dimen.type_body_medium),
        lineHeight = spResource(R.dimen.line_height_20),
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = spResource(R.dimen.type_caption),
        lineHeight = spResource(R.dimen.line_height_16),
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = spResource(R.dimen.type_body_medium),
        lineHeight = spResource(R.dimen.line_height_20),
        letterSpacing = trackingResource(R.dimen.tracking_compose_label_sm),
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = spResource(R.dimen.type_caption),
        lineHeight = spResource(R.dimen.line_height_16),
        letterSpacing = trackingResource(R.dimen.tracking_compose_label_md),
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = spResource(R.dimen.type_overline),
        lineHeight = spResource(R.dimen.line_height_14),
        letterSpacing = trackingResource(R.dimen.tracking_compose_label_lg),
    ),
)

/** Kept for call sites that used the old top-level val; resolved per composition now. */
val EventQrTypography: Typography
    @Composable @ReadOnlyComposable get() = eventQrTypography()
