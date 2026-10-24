package com.thedavelopers.eventqr.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.inComposition
import com.thedavelopers.eventqr.ui.resolvedSpacing
import com.thedavelopers.eventqr.ui.roleColor
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

        assertEquals(roleColor(R.color.primary), light.primary)
        assertEquals(roleColor(R.color.on_primary), light.onPrimary)
        assertEquals(roleColor(R.color.secondary), light.secondary)
        assertEquals(roleColor(R.color.background), light.background)
        assertEquals(roleColor(R.color.text_primary), light.onBackground)
        assertEquals(roleColor(R.color.surface), light.surface)
        assertEquals(roleColor(R.color.text_primary), light.onSurface)
        assertEquals(roleColor(R.color.background), light.surfaceVariant)
        assertEquals(roleColor(R.color.text_secondary), light.onSurfaceVariant)
        assertEquals(roleColor(R.color.error), light.error)
        assertEquals(roleColor(R.color.on_primary), light.onError)
        assertEquals(roleColor(R.color.error_container), light.errorContainer)
        assertEquals(roleColor(R.color.on_error_container), light.onErrorContainer)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemDarkMode_yieldsLightColorScheme() {
        val scheme = composeTestRule.themeColorScheme {}

        assertEquals(roleColor(R.color.surface), scheme.surface)
        assertEquals(roleColor(R.color.primary), scheme.primary)
    }

    @Test
    fun auditedTokenValues_arePinned() {
        // Pinned values of the roles behind the Compose names: a palette edit must be a conscious change here too.
        val tokens = composeTestRule.inComposition {
            listOf(StatusRejectedRed, BrandPrimary, BrandPurple, TextPrimary)
        }

        assertEquals(Color(0xFFC62828), tokens[0])
        assertEquals(Color(0xFF2D2A7C), tokens[1])
        assertEquals(Color(0xFF5B25C9), tokens[2])
        assertEquals(Color(0xFF111827), tokens[3])
    }

    @Test
    fun brandTertiary_isUsedForLightSchemeTertiaryRole() {
        val light = composeTestRule.themeColorScheme {}

        assertEquals(roleColor(R.color.warning_dark), light.tertiary)
    }

    @Test
    fun statusAccentTokens_arePinned() {
        val accents = composeTestRule.inComposition {
            listOf(
                EventAccentActive,
                EventAccentActiveFill,
                EventAccentCompleted,
                EventAccentCompletedFill,
                EventAccentUpcoming,
                EventAccentUpcomingFill,
            )
        }

        assertEquals(Color(0xFF065F46), accents[0])
        assertEquals(Color(0xFF059669), accents[1])
        assertEquals(Color(0xFF374151), accents[2])
        assertEquals(Color(0xFF67707F), accents[3]) // D4: text_secondary is the single AA grey 67707F (was 6B7280)
        assertEquals(Color(0xFF92400E), accents[4])
        assertEquals(Color(0xFFB45309), accents[5])
    }

    @Test
    fun rowTheme_providesMaterialColorScheme() {
        val rowScheme = rowThemeColorScheme()

        assertEquals(roleColor(R.color.primary), rowScheme.primary)
        assertEquals(roleColor(R.color.text_primary), rowScheme.onSurface)
    }

    @Test
    fun rowTheme_providesDefaultSpacing() {
        val spacing = rowThemeSpacing()

        assertEquals(pinnedSpacing(), spacing)
    }

    @Test
    fun lightColorScheme_mapsTertiaryContainerToTheAmberFamily() {
        val light = composeTestRule.themeColorScheme {}

        assertEquals(roleColor(R.color.warning_container), light.tertiaryContainer)
        assertEquals(roleColor(R.color.on_warning_container), light.onTertiaryContainer)
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

        assertEquals(pinnedSpacing(), spacing)
        assertEquals(48.dp, spacing.minTouchTarget)
        assertEquals(96.dp, spacing.cardMinHeight)
        assertEquals(16.dp, spacing.cardCornerRadius)
    }

    @Test
    fun customSpacing_isProvidedToContent() {
        val spacing = composeTestRule.resolvedSpacing(spacing = pinnedSpacing().copy(minTouchTarget = 64.dp))

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

    /**
     * The spacing the app shipped with before it was read from @dimen tokens, written out as literals so a token change that
     * moves a number fails here.
     */
    private fun pinnedSpacing() = EventQrSpacing(
        default = 0.dp,
        nano = 2.dp,
        extraSmall = 4.dp,
        micro = 6.dp,
        small = 8.dp,
        mediumSmall = 12.dp,
        medium = 16.dp,
        mediumLarge = 20.dp,
        large = 24.dp,
        extraLarge = 32.dp,
        huge = 48.dp,
        minTouchTarget = 48.dp,
        screenHorizontalPadding = 16.dp,
        screenVerticalPadding = 16.dp,
        cardCornerRadius = 16.dp,
        cardCornerRadiusSmall = 12.dp,
        chipCornerRadius = 20.dp,
        pillCornerRadius = 999.dp,
        inputHeightMin = 48.dp,
        buttonHeightMin = 48.dp,
        cardMinHeight = 96.dp,
        listItemMinHeight = 72.dp,
        cardElevation = 2.dp,
        navElevation = 8.dp,
        cardContentPadding = 16.dp,
        cardContentGap = 14.dp,
        badgeHorizontalPadding = 10.dp,
        badgeContentPadding = 4.dp,
        dateBadgeSize = 54.dp,
        iconSizeSmall = 14.dp,
        iconSizeMedium = 20.dp,
        iconSizeLarge = 24.dp,
        iconSizeXLarge = 36.dp,
        iconContainerSmall = 36.dp,
        iconContainerLarge = 72.dp,
        brandLogoSize = 72.dp,
        emptyStateVerticalPadding = 40.dp,
        progressBarHeight = 6.dp,
        progressBarCornerRadius = 3.dp,
        chipBorderWidth = 1.dp,
    )

    @Test
    fun typography_matchesTheShippedScale() {
        val t = composeTestRule.inComposition { MaterialTheme.typography }

        fun check(style: TextStyle, size: Int, line: Int, weight: FontWeight, tracking: Float?) {
            assertEquals(size.sp, style.fontSize)
            assertEquals(line.sp, style.lineHeight)
            assertEquals(weight, style.fontWeight)
            assertEquals(FontFamily.SansSerif, style.fontFamily)
            if (tracking == null) assertEquals(TextUnit.Unspecified, style.letterSpacing) else assertEquals(tracking.sp, style.letterSpacing)
        }
        check(t.headlineLarge, 28, 36, FontWeight.Bold, -0.5f)
        check(t.headlineMedium, 24, 32, FontWeight.Bold, -0.3f)
        check(t.headlineSmall, 20, 28, FontWeight.Bold, null)
        check(t.titleLarge, 18, 26, FontWeight.SemiBold, null)
        check(t.titleMedium, 16, 24, FontWeight.SemiBold, null)
        check(t.titleSmall, 14, 20, FontWeight.Medium, null)
        check(t.bodyLarge, 16, 24, FontWeight.Normal, null)
        check(t.bodyMedium, 14, 20, FontWeight.Normal, null)
        check(t.bodySmall, 12, 16, FontWeight.Normal, null)
        check(t.labelLarge, 14, 20, FontWeight.SemiBold, 0.1f)
        check(t.labelMedium, 12, 16, FontWeight.Medium, 0.2f)
        check(t.labelSmall, 11, 14, FontWeight.Medium, 0.3f)
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
