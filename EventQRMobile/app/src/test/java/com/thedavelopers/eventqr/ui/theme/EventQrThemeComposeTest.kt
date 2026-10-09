package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.resolvedSpacing
import com.thedavelopers.eventqr.ui.themeColorScheme
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Robolectric

private const val MIN_TEXT_CONTRAST = 4.5f

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventQrThemeComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lightColorScheme_mapsAuditedSemanticTokens() {
        val light = composeTestRule.themeColorScheme {}

        assertEquals(BrandPrimary, light.primary)
        assertEquals(TextOnPrimary, light.onPrimary)
        assertEquals(BrandPurple, light.secondary)
        assertEquals(BackgroundLight, light.background)
        assertEquals(TextPrimary, light.onBackground)
        assertEquals(PaperWhite, light.surface)
        assertEquals(TextPrimary, light.onSurface)
        assertEquals(SurfaceAlt, light.surfaceVariant)
        assertEquals(TextSecondary, light.onSurfaceVariant)
        assertEquals(StatusRejectedRed, light.error)
        assertEquals(TextOnPrimary, light.onError)
        assertEquals(StatusRejectedRedBg, light.errorContainer)
        assertEquals(StatusRejectedRedText, light.onErrorContainer)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemDarkMode_yieldsLightColorScheme() {
        val scheme = composeTestRule.themeColorScheme {}

        assertEquals(PaperWhite, scheme.surface)
        assertEquals(BrandPrimary, scheme.primary)
    }

    @Test
    fun auditedTokenValues_arePinned() {
        assertEquals(Color(0xFFB91C1C), StatusRejectedRed)
    }

    @Test
    fun brandTertiary_isUsedForLightSchemeTertiaryRole() {
        val light = composeTestRule.themeColorScheme {}

        assertEquals(BrandTertiary, light.tertiary)
    }

    @Test
    fun statusAccentTokens_arePinned() {
        assertEquals(Color(0xFF065F46), EventAccentActive)
        assertEquals(Color(0xFF059669), EventAccentActiveFill)
        assertEquals(Color(0xFF374151), EventAccentCompleted)
        assertEquals(Color(0xFF6B7280), EventAccentCompletedFill)
        assertEquals(Color(0xFF92400E), EventAccentUpcoming)
        assertEquals(Color(0xFFB45309), EventAccentUpcomingFill)
    }

    @Test
    fun rowTheme_providesMaterialColorScheme() {
        val rowScheme = rowThemeColorScheme()

        assertEquals(BrandPrimary, rowScheme.primary)
        assertEquals(TextPrimary, rowScheme.onSurface)
    }

    @Test
    fun rowTheme_providesDefaultSpacing() {
        val spacing = rowThemeSpacing()

        assertEquals(EventQrSpacing(), spacing)
    }

    @Test
    fun lightColorScheme_mapsTertiaryContainerToTheAmberFamily() {
        val light = composeTestRule.themeColorScheme {}

        assertEquals(StatusPendingAmberBg, light.tertiaryContainer)
        assertEquals(StatusPendingAmberText, light.onTertiaryContainer)
        assertNotEquals(Color(0xFFFFD8E4), light.tertiaryContainer)
    }

    @Test
    fun tertiaryContainerPair_isReadable() {
        val light = lightColorScheme()

        val ratio = contrastRatio(light.onTertiaryContainer, light.tertiaryContainer)

        assertTrue("light tertiary container contrast was $ratio", ratio >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun primaryContainerPair_isReadable() {
        val light = lightColorScheme()

        val ratio = contrastRatio(light.onPrimaryContainer, light.primaryContainer)

        assertTrue("light primary container contrast was $ratio", ratio >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun defaultSpacing_isProvidedToContent() {
        val spacing = composeTestRule.resolvedSpacing()

        assertEquals(EventQrSpacing(), spacing)
        assertEquals(48.dp, spacing.minTouchTarget)
        assertEquals(96.dp, spacing.cardMinHeight)
        assertEquals(16.dp, spacing.cardCornerRadius)
    }

    @Test
    fun customSpacing_isProvidedToContent() {
        val spacing = composeTestRule.resolvedSpacing(spacing = EventQrSpacing(minTouchTarget = 64.dp))

        assertEquals(64.dp, spacing.minTouchTarget)
        assertEquals(96.dp, spacing.cardMinHeight)
    }

    @Test
    fun systemBarAppearance_setsDarkStatusBars() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance()

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemBarAppearance_staysDarkInSystemNightMode() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance()

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    @Test
    fun systemBarAppearance_darkStatusBarsRequested() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance(lightStatusBars = false)
        activity.applyRequestedEventQrSystemBarAppearance()

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    private fun lightColorScheme(): ColorScheme {
        var captured: ColorScheme? = null
        composeTestRule.setContent {
            EventQrTheme {
                captured = MaterialTheme.colorScheme
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(captured)
    }

    private fun rowThemeColorScheme(): ColorScheme {
        var captured: ColorScheme? = null
        composeTestRule.setContent {
            EventQrRowTheme {
                captured = MaterialTheme.colorScheme
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(captured)
    }

    private fun rowThemeSpacing(): EventQrSpacing {
        var captured: EventQrSpacing? = null
        composeTestRule.setContent {
            EventQrRowTheme {
                captured = LocalSpacing.current
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(captured)
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(color: Color): Float {
        return 0.2126f * linearize(color.red) +
            0.7152f * linearize(color.green) +
            0.0722f * linearize(color.blue)
    }

    private fun linearize(channel: Float): Float {
        return if (channel <= 0.03928f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).pow(2.4f)
    }
}
