package com.thedavelopers.eventqr.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenBg
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenBgDark
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenText
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenTextDark
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val MIN_TEXT_CONTRAST = 4.5f

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StatusBadgeComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersDefaultLabelForEveryStatus() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventBadgeStatus.entries.forEach { status ->
                    StatusBadge(status = status)
                }
            }
        }

        listOf(
            "Pending",
            "Approved",
            "Rejected",
            "Active",
            "Completed",
            "Registered",
            "Upcoming",
            "Cancelled",
            "Draft",
            "Unknown",
        ).forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun draftAndUnknown_renderDistinctLabelsFromPending() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                StatusBadge(status = EventBadgeStatus.DRAFT)
                StatusBadge(status = EventBadgeStatus.UNKNOWN)
            }
        }

        composeTestRule.onNodeWithText("Draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
    }

    @Test
    fun customLabel_overridesDefaultLabel() {
        setBadge(EventBadgeStatus.PENDING, customLabel = "Awaiting Staff")

        composeTestRule.onNodeWithText("Awaiting Staff").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
    }

    @Test
    fun customLabel_appliesToEveryStatus() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventBadgeStatus.entries.forEach { status ->
                    StatusBadge(status = status, customLabel = status.name)
                }
            }
        }

        EventBadgeStatus.entries.forEach { status ->
            composeTestRule.onNodeWithText(status.name).assertIsDisplayed()
        }
    }

    @Test
    fun hiddenIcon_stillRendersLabel() {
        setBadge(EventBadgeStatus.ACTIVE, showIcon = false)

        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
    }

    @Test
    fun shownIcon_doesNotDropTheLabel() {
        setBadge(EventBadgeStatus.ACTIVE, showIcon = true)

        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
    }

    @Test
    fun badge_meetsPaddedHeight() {
        setBadge(EventBadgeStatus.COMPLETED)

        composeTestRule.onNode(hasText("Completed")).assertHeightIsAtLeast(20.dp)
    }

    @Test
    fun badge_hasNoClickAction() {
        setBadge(EventBadgeStatus.APPROVED)

        composeTestRule.onNode(hasText("Approved")).assertHasNoClickAction()
    }

    @Test
    fun pendingAndUpcoming_renderDistinctLabels() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                StatusBadge(status = EventBadgeStatus.PENDING)
                StatusBadge(status = EventBadgeStatus.UPCOMING)
            }
        }

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
    }

    @Test
    fun cancelledAndRejected_renderDistinctLabels() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                StatusBadge(status = EventBadgeStatus.REJECTED)
                StatusBadge(status = EventBadgeStatus.CANCELLED)
            }
        }

        composeTestRule.onNodeWithText("Rejected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelled").assertIsDisplayed()
    }

    @Test
    fun darkTheme_usesDarkBadgeContainersNotTheLightPalette() {
        val style = resolveStylesFor(darkTheme = true).getValue(EventBadgeStatus.APPROVED)

        assertEquals(StatusApprovedGreenBgDark, style.backgroundColor)
        assertEquals(StatusApprovedGreenTextDark, style.textColor)
    }

    @Test
    fun lightTheme_usesLightBadgeContainers() {
        val style = resolveStylesFor(darkTheme = false).getValue(EventBadgeStatus.APPROVED)

        assertEquals(StatusApprovedGreenBg, style.backgroundColor)
        assertEquals(StatusApprovedGreenText, style.textColor)
    }

    @Test
    fun darkTheme_neverFallsBackToLightBadgeColors() {
        val (light, dark) = resolveStylesForBothThemes()

        assertEquals(EventBadgeStatus.entries.toSet(), light.keys)
        assertEquals(EventBadgeStatus.entries.toSet(), dark.keys)
        EventBadgeStatus.entries.forEach { status ->
            assertNotEquals(
                "$status reused the light container",
                light.getValue(status).backgroundColor,
                dark.getValue(status).backgroundColor,
            )
            assertNotEquals(
                "$status reused the light label color",
                light.getValue(status).textColor,
                dark.getValue(status).textColor,
            )
        }
    }

    @Test
    fun darkTheme_badgeLabelContrastIsReadable() {
        val dark = resolveStylesFor(darkTheme = true)

        EventBadgeStatus.entries.forEach { status ->
            val style = dark.getValue(status)
            val contrast = contrastRatio(style.textColor, style.backgroundColor)

            assertTrue("$status dark badge contrast was $contrast", contrast >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun lightTheme_badgeLabelContrastIsReadable() {
        val light = resolveStylesFor(darkTheme = false)

        EventBadgeStatus.entries.forEach { status ->
            val style = light.getValue(status)
            val contrast = contrastRatio(style.textColor, style.backgroundColor)

            assertTrue("$status light badge contrast was $contrast", contrast >= MIN_TEXT_CONTRAST)
        }
    }

    private fun resolveStylesFor(darkTheme: Boolean): Map<EventBadgeStatus, BadgeStyle> {
        var captured: Map<EventBadgeStatus, BadgeStyle> = emptyMap()
        composeTestRule.setContent {
            EventQrTheme(darkTheme = darkTheme) {
                captured = EventBadgeStatus.entries.associateWith { badgeStyle(it) }
            }
        }
        composeTestRule.waitForIdle()
        return captured
    }

    private fun resolveStylesForBothThemes(): Pair<Map<EventBadgeStatus, BadgeStyle>, Map<EventBadgeStatus, BadgeStyle>> {
        var light: Map<EventBadgeStatus, BadgeStyle> = emptyMap()
        var dark: Map<EventBadgeStatus, BadgeStyle> = emptyMap()
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                light = EventBadgeStatus.entries.associateWith { badgeStyle(it) }
            }
            EventQrTheme(darkTheme = true) {
                dark = EventBadgeStatus.entries.associateWith { badgeStyle(it) }
            }
        }
        composeTestRule.waitForIdle()
        return light to dark
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

    private fun setBadge(status: EventBadgeStatus, customLabel: String? = null, showIcon: Boolean = true) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                StatusBadge(status = status, customLabel = customLabel, showIcon = showIcon)
            }
        }
    }
}
