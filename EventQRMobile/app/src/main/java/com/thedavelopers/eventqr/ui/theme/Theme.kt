package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import com.thedavelopers.eventqr.R

/*
 * Compose theme for the redesigned app (§3.2, §3.5).
 *
 * The scheme reads ui_* roles only. That is what makes dark mode a one-file addition in Phase 5:
 * add res/values-night/colors.xml, repoint the same ui_* names, then flip [EventQrTheme]'s
 * `darkTheme` default to `isSystemInDarkTheme()`. No Compose file and no layout changes.
 *
 * `darkTheme` is therefore already wired (and already correct) but defaults to false, because the
 * ~55 screens that are not migrated yet still use the legacy light-only vocabulary.
 */

@Composable
@ReadOnlyComposable
private fun eventQrColorScheme(dark: Boolean): ColorScheme {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = UiPrimary,
        onPrimary = UiOnPrimary,
        primaryContainer = UiPrimaryContainer,
        onPrimaryContainer = UiOnPrimaryContainer,
        inversePrimary = UiPrimaryFocusRing,
        secondary = UiPrimaryContainer,
        onSecondary = UiOnPrimaryContainer,
        secondaryContainer = UiPrimaryContainer,
        onSecondaryContainer = UiOnPrimaryContainer,
        tertiary = UiAccentDeep,
        onTertiary = UiOnPrimary,
        tertiaryContainer = UiAccentContainer,
        onTertiaryContainer = UiAccentDeep,
        background = UiBackground,
        onBackground = UiTextPrimary,
        surface = UiSurface,
        onSurface = UiTextPrimary,
        surfaceVariant = UiSurfaceSunken,
        onSurfaceVariant = UiTextSecondary,
        surfaceContainer = UiSurface,
        surfaceContainerHigh = UiSurface,
        surfaceContainerLow = UiBackground,
        surfaceTint = UiPrimary,
        inverseSurface = UiTextPrimary,
        inverseOnSurface = UiSurface,
        outline = UiOutlineInteractive,
        outlineVariant = UiHairline,
        scrim = UiScrim,
        error = UiError,
        onError = UiOnPrimary,
        errorContainer = UiErrorContainer,
        onErrorContainer = UiOnErrorContainer,
    )
}

/** The fourteen-role type scale, provided through [LocalType]. */
val LocalType = staticCompositionLocalOf<EventQrType> {
    error("EventQrType is not provided: wrap the content in EventQrTheme or EventQrRowTheme")
}

@Composable
fun EventQrTheme(
    spacing: EventQrSpacing? = null,
    grid: EventQrGrid? = null,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.applyRequestedEventQrSystemBarAppearance()
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides (spacing ?: eventQrSpacing()),
        LocalGrid provides (grid ?: eventQrGrid()),
        LocalType provides eventQrType(),
    ) {
        MaterialTheme(
            colorScheme = eventQrColorScheme(darkTheme),
            typography = eventQrTypography(),
            content = content,
        )
    }
}

/** Row / cell host: cheap version for ComposeView inside a RecyclerView item. */
@Composable
fun EventQrRowTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides eventQrSpacing(),
        LocalGrid provides eventQrGrid(),
        LocalType provides eventQrType(),
    ) {
        MaterialTheme(
            colorScheme = eventQrColorScheme(darkTheme),
            typography = eventQrTypography(),
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
            decor.postDelayed(update, decor.integer(R.integer.motion_settle_ms).toLong())
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

fun View.applyEventQrBottomInsetPadding() {
    val basePaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = basePaddingBottom + navBottom)
        insets
    }
    if (isAttachedToWindow) ViewCompat.requestApplyInsets(this) else doOnAttach { ViewCompat.requestApplyInsets(it) }
}
