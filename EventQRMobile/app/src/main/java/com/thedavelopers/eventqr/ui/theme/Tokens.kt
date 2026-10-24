package com.thedavelopers.eventqr.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.IntegerRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.thedavelopers.eventqr.R
import kotlin.math.roundToInt

/**
 * Resolves a colour ROLE (`R.color.primary`, `R.color.surface`, `R.color.text_secondary`, ...) to an ARGB int.
 *
 * This is the one sanctioned way for View-based Kotlin code to read a colour: pass a role from
 * `res/values/colors.xml`, never a hex literal and never a palette primitive. It replaces the platform
 * colour-parsing helpers and the android.graphics constants, and works from any `Context` (activity, adapter,
 * view helper).
 */
fun Context.color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

// ---------------------------------------------------------------------------------------------------------------------
// Dimension tokens (res/values/dimens.xml, integers.xml). View-based Kotlin must read sizes through these instead of
// `dp(N)`, `value * density` or applyDimension, so every number has a name and a single definition.
//
// Which one to use (they differ in rounding, keep the conversion the call site used before):
//   dimen        float px                          sizes that were Float (elevation, radius, text size, stroke as float)
//   dimenPx      Resources.getDimensionPixelSize   == roundToInt(value * density)   (min 1px when the dimen is > 0)
//   dimenOffset  Resources.getDimensionPixelOffset == (value * density).toInt()     (truncating)
// ---------------------------------------------------------------------------------------------------------------------

/** Dimension token as a float in px (no rounding). */
fun Context.dimen(@DimenRes id: Int): Float = resources.getDimension(id)

/** Dimension token rounded to the nearest px (same as `(dp * density).roundToInt()`). */
fun Context.dimenPx(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)

/** Dimension token truncated to px (same as `(dp * density).toInt()`). */
fun Context.dimenOffset(@DimenRes id: Int): Int = resources.getDimensionPixelOffset(id)

/** Unitless float token (`<item type="dimen" format="float">`: alpha, scale, tracking). */
fun Context.dimenFloat(@DimenRes id: Int): Float = ResourcesCompat.getFloat(resources, id)

/** Integer token from res/values/integers.xml (motion durations in ms). */
fun Context.integer(@IntegerRes id: Int): Int = resources.getInteger(id)

/**
 * Runtime dp -> px (rounded) for numbers that come from data / config rather than the design scale, e.g. the printed-ID
 * preview geometry derived from IdCardLayoutConfig. Design values use `dimenPx(R.dimen.*)` instead.
 */
fun Context.configDpToPx(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun View.dimen(@DimenRes id: Int): Float = context.dimen(id)
fun View.dimenPx(@DimenRes id: Int): Int = context.dimenPx(id)
fun View.dimenOffset(@DimenRes id: Int): Int = context.dimenOffset(id)
fun View.dimenFloat(@DimenRes id: Int): Float = context.dimenFloat(id)
fun View.integer(@IntegerRes id: Int): Int = context.integer(id)

/** Sets the text size from a `type_*` token (sp dimen resolved with the user's font scale, like `textSize = Nf`). */
fun TextView.setTextSizeRes(@DimenRes id: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(id))
}

/** Makes the current typeface bold. The only place `Typeface.BOLD` may appear in View code. */
fun TextView.strong() {
    setTypeface(typeface, Typeface.BOLD)
}

/** Medium-weight sans typeface (`sans-serif-medium`), for text that has no TextAppearance.EventQR.* style. */
fun TextView.mediumWeight() {
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

/** Resets to the regular weight of [base] (default: the current typeface), e.g. to undo a style's bold. */
fun TextView.regular(base: Typeface? = typeface) {
    setTypeface(base, Typeface.NORMAL)
}

/** The platform default bold typeface ([Typeface.DEFAULT_BOLD]), independent of the view's current typeface. */
fun TextView.defaultBold() {
    typeface = Typeface.DEFAULT_BOLD
}

/** Bold [StyleSpan] for partial-text emphasis in a SpannableString. */
fun boldSpan(): StyleSpan = StyleSpan(Typeface.BOLD)

/**
 * Rounded rectangle background: [radius] and [stroke] are dimen tokens (`radius_*`, `stroke_*`); the stroke is only drawn when
 * [strokeColor] is non-null. Radius is float px, stroke width is rounded to whole px (same as the old `rounded(...)` helper).
 */
fun Context.roundedRect(
    color: Int,
    @DimenRes radius: Int,
    strokeColor: Int? = null,
    @DimenRes stroke: Int = R.dimen.stroke_hairline,
): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dimen(radius)
    strokeColor?.let { setStroke(dimenPx(stroke), it) }
}
