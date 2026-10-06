package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import android.content.res.Configuration
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
    fun darkTheme_yieldsDifferentColorSchemeThanLightTheme() {
        var light: ColorScheme? = null
        var dark: ColorScheme? = null

        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                light = MaterialTheme.colorScheme
                EventQrTheme(darkTheme = true) {
                    dark = MaterialTheme.colorScheme
                }
            }
        }
        composeTestRule.waitForIdle()

        assertNotEquals(requireNotNull(light), requireNotNull(dark))
        assertEquals(DarkSurface, requireNotNull(dark).surface)
        assertEquals(PaperWhite, requireNotNull(light).surface)
    }

    @Test
    fun lightColorScheme_mapsAuditedSemanticTokens() {
        val light = composeTestRule.themeColorScheme(darkTheme = false) {}

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
    fun darkColorScheme_mapsAuditedSemanticTokens() {
        val dark = composeTestRule.themeColorScheme(darkTheme = true) {}

        assertEquals(DarkPrimary, dark.primary)
        assertEquals(DarkBackground, dark.background)
        assertEquals(DarkSurface, dark.surface)
        assertEquals(DarkOnSurface, dark.onSurface)
        assertEquals(DarkSurfaceVariant, dark.surfaceVariant)
        assertEquals(DarkOnSurfaceVariant, dark.onSurfaceVariant)
        assertEquals(DarkError, dark.error)
    }

    @Test
    fun auditedTokenValues_arePinned() {
        assertEquals(Color(0xFFB91C1C), StatusRejectedRed)
    }

    @Test
    fun brandTertiary_isUsedForLightSchemeTertiaryRole() {
        val light = composeTestRule.themeColorScheme(darkTheme = false) {}

        assertEquals(BrandTertiary, light.tertiary)
    }

    @Test
    fun darkScheme_keepsItsOwnTertiaryRole() {
        val dark = composeTestRule.themeColorScheme(darkTheme = true) {}

        assertEquals(DarkTertiary, dark.tertiary)
    }

    @Test
    fun statusAccentTokens_arePinned() {
        assertEquals(Color(0xFF065F46), EventAccentActive)
        assertEquals(Color(0xFF059669), EventAccentActiveFill)
        assertEquals(Color(0xFF374151), EventAccentCompleted)
        assertEquals(Color(0xFF6B7280), EventAccentCompletedFill)
        assertEquals(Color(0xFF2D2A7C), EventAccentUpcoming)
        assertEquals(Color(0xFF2563EB), EventAccentUpcomingFill)
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
    fun rowTheme_darkTheme_switchesColorScheme() {
        val rowScheme = rowThemeColorScheme(darkTheme = true)

        assertEquals(DarkSurface, rowScheme.surface)
    }

    @Test
    fun lightColorScheme_mapsTertiaryContainerToTheAmberFamily() {
        val light = composeTestRule.themeColorScheme(darkTheme = false) {}

        assertEquals(StatusPendingAmberBg, light.tertiaryContainer)
        assertEquals(StatusPendingAmberText, light.onTertiaryContainer)
        assertNotEquals(Color(0xFFFFD8E4), light.tertiaryContainer)
    }

    @Test
    fun darkColorScheme_mapsTertiaryContainerToTheAmberFamily() {
        val dark = composeTestRule.themeColorScheme(darkTheme = true) {}

        assertEquals(StatusPendingAmberBgDark, dark.tertiaryContainer)
        assertEquals(StatusPendingAmberTextDark, dark.onTertiaryContainer)
    }

    @Test
    fun tertiaryContainerPair_isReadableInBothThemes() {
        val (light, dark) = bothThemeColorSchemes()

        val lightRatio = contrastRatio(light.onTertiaryContainer, light.tertiaryContainer)
        val darkRatio = contrastRatio(dark.onTertiaryContainer, dark.tertiaryContainer)

        assertTrue("light tertiary container contrast was $lightRatio", lightRatio >= MIN_TEXT_CONTRAST)
        assertTrue("dark tertiary container contrast was $darkRatio", darkRatio >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun primaryContainerPair_isReadableInBothThemes() {
        val (light, dark) = bothThemeColorSchemes()

        val lightRatio = contrastRatio(light.onPrimaryContainer, light.primaryContainer)
        val darkRatio = contrastRatio(dark.onPrimaryContainer, dark.primaryContainer)

        assertTrue("light primary container contrast was $lightRatio", lightRatio >= MIN_TEXT_CONTRAST)
        assertTrue("dark primary container contrast was $darkRatio", darkRatio >= MIN_TEXT_CONTRAST)
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
    @Config(qualifiers = "notnight")
    fun systemBarAppearance_lightTheme_setsLightBars() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance(darkTheme = false)

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemBarAppearance_darkTheme_setsDarkBars() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance(darkTheme = true)

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemBarAppearance_defaultsToSystemNightMode() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance()

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(controller.isAppearanceLightStatusBars)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun systemBarAppearance_defaultsToSystemDayMode() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.applyEventQrSystemBarAppearance()

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue(controller.isAppearanceLightStatusBars)
    }

    @Test
    @Config(qualifiers = "night")
    fun nightModeDetection_followsUiModeNightYes() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertEquals(Configuration.UI_MODE_NIGHT_YES, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertTrue(invokeNightModeProbe(activity))
    }

    @Test
    @Config(qualifiers = "notnight")
    fun nightModeDetection_followsUiModeNightNo() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertEquals(Configuration.UI_MODE_NIGHT_NO, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertFalse(invokeNightModeProbe(activity))
    }

    private fun invokeNightModeProbe(activity: Activity): Boolean {
        val method = Class.forName("com.thedavelopers.eventqr.ui.theme.ThemeKt")
            .getDeclaredMethod("isEventQrNightMode", Activity::class.java)
        method.isAccessible = true
        return method.invoke(null, activity) as Boolean
    }

    private fun bothThemeColorSchemes(): Pair<ColorScheme, ColorScheme> {
        var light: ColorScheme? = null
        var dark: ColorScheme? = null
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                light = MaterialTheme.colorScheme
                EventQrTheme(darkTheme = true) {
                    dark = MaterialTheme.colorScheme
                }
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(light) to requireNotNull(dark)
    }

    private fun rowThemeColorScheme(darkTheme: Boolean = false): ColorScheme {
        var captured: ColorScheme? = null
        composeTestRule.setContent {
            EventQrRowTheme(darkTheme = darkTheme) {
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
