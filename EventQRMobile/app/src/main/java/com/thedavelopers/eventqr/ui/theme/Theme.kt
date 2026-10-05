package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = TextOnPrimary,
    secondary = BrandPurple,
    onSecondary = TextOnPrimary,
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    spacing: EventQrSpacing = EventQrSpacing(),
    content: @Composable () -> Unit,
) {
    // Current app design is clean light-first with rich brand indigo
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
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
