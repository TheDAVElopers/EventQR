package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = StatusPendingAmberBgDark,
    onTertiaryContainer = StatusPendingAmberTextDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

@Composable
fun EventQrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    spacing: EventQrSpacing = EventQrSpacing(),
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme
    val colorScheme = eventQrColorScheme(useDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.applyEventQrSystemBarAppearance(useDark)
        }
    }

    CompositionLocalProvider(LocalSpacing provides spacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EventQrTypography,
            content = content,
        )
    }
}

@Composable
fun EventQrRowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides EventQrSpacing()) {
        MaterialTheme(
            colorScheme = eventQrColorScheme(darkTheme),
            typography = EventQrTypography,
            content = content,
        )
    }
}

fun Activity.applyEventQrSystemBarAppearance(darkTheme: Boolean = isEventQrNightMode()) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}

private fun Activity.isEventQrNightMode(): Boolean {
    val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightMode == Configuration.UI_MODE_NIGHT_YES
}

private fun eventQrColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) DarkColorScheme else LightColorScheme
