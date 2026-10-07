package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import com.thedavelopers.eventqr.R

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = TextOnPrimary,
    secondary = BrandPurple,
    onSecondary = TextOnPrimary,
    tertiary = BrandTertiary,
    onTertiary = TextOnPrimary,
    tertiaryContainer = StatusPendingAmberBg,
    onTertiaryContainer = StatusPendingAmberText,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = PaperWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = OutlineVariant,
    error = StatusRejectedRed,
    onError = TextOnPrimary,
    errorContainer = StatusRejectedRedBg,
    onErrorContainer = StatusRejectedRedText,
)

@Composable
fun EventQrTheme(
    spacing: EventQrSpacing = EventQrSpacing(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.applyRequestedEventQrSystemBarAppearance()
        }
    }

    CompositionLocalProvider(LocalSpacing provides spacing) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = EventQrTypography,
            content = content,
        )
    }
}

@Composable
fun EventQrRowTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides EventQrSpacing()) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = EventQrTypography,
            content = content,
        )
    }
}

fun Activity.applyEventQrSystemBarAppearance(lightStatusBars: Boolean = false) {
    window.decorView.setTag(R.id.eventqr_light_status_bars, lightStatusBars)
    applyRequestedEventQrSystemBarAppearance()
}

internal fun Activity.applyRequestedEventQrSystemBarAppearance() {
    val lightStatusBars = window.decorView.getTag(R.id.eventqr_light_status_bars) as? Boolean ?: false
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = lightStatusBars
        isAppearanceLightNavigationBars = true
    }
    applyEventQrAdaptiveStatusBar()
}

// The status bar is transparent, so whatever the header paints shows behind it. Only the icon contrast has to adapt.
private fun Activity.applyEventQrAdaptiveStatusBar() {
    val decor = window.decorView
    val update = Runnable { updateEventQrStatusBarIconContrast() }
    if (decor.getTag(R.id.eventqr_status_bar_adaptive) != true) {
        decor.setTag(R.id.eventqr_status_bar_adaptive, true)
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            decor.removeCallbacks(update)
            decor.postDelayed(update, 80)
        }
    }
    decor.post(update)
}

private fun Activity.updateEventQrStatusBarIconContrast() {
    val decor = window.decorView
    val statusTop = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: return
    if (statusTop <= 0 || decor.width <= 0) return
    val scale = 0.1f
    val bitmap = Bitmap.createBitmap(
        (decor.width * scale).toInt().coerceAtLeast(1),
        (statusTop * scale).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    decor.draw(canvas)
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    val luminance = pixels.map { 0.299 * Color.red(it) + 0.587 * Color.green(it) + 0.114 * Color.blue(it) }.average() / 255.0
    WindowCompat.getInsetsController(window, decor).isAppearanceLightStatusBars = luminance > 0.5
}

fun View.applyEventQrTopInsetPadding() {
    val basePaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = basePaddingTop + statusTop)
        insets
    }
    // Views attached after the first insets pass (e.g. headers built after a network call) would otherwise never get them.
    // requestApplyInsets is a no-op on a detached view, so it has to wait for attach.
    if (isAttachedToWindow) ViewCompat.requestApplyInsets(this) else doOnAttach { ViewCompat.requestApplyInsets(it) }
}
