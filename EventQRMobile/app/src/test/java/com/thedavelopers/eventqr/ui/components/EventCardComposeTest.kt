package com.thedavelopers.eventqr.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
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
class EventCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setCard(
        status: EventBadgeStatus = EventBadgeStatus.UPCOMING,
        title: String = "Design Systems Summit",
        day: String = "12",
        month: String = "mar",
        time: String = "09:00 AM",
        location: String = "Manila Convention Center",
        registeredCount: Int? = null,
        capacity: Int? = null,
        statusLabel: String? = null,
        onClick: (() -> Unit)? = null,
        trailingAction: (@Composable () -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            EventQrTheme {
                EventCard(
                    title = title,
                    status = status,
                    day = day,
                    month = month,
                    time = time,
                    location = location,
                    registeredCount = registeredCount,
                    capacity = capacity,
                    statusLabel = statusLabel,
                    onClick = onClick,
                    trailingAction = trailingAction,
                )
            }
        }
    }

    @Test
    fun eventCardAccent_everyStatus_isUniqueOutsideTheDeclaredSharingGroups() {
        val accentGroups = listOf(
            setOf(EventBadgeStatus.REJECTED, EventBadgeStatus.CANCELLED),
            setOf(EventBadgeStatus.COMPLETED, EventBadgeStatus.DRAFT, EventBadgeStatus.UNKNOWN),
            setOf(EventBadgeStatus.ACTIVE),
            setOf(EventBadgeStatus.UPCOMING, EventBadgeStatus.PENDING),
            setOf(EventBadgeStatus.APPROVED),
            setOf(EventBadgeStatus.REGISTERED),
        )

        assertEquals(EventBadgeStatus.entries.toSet(), accentGroups.flatten().toSet())

        val groupColors = accentGroups.map { group ->
            val containers = group.map { eventCardAccent(it).container }.toSet()
            val fills = group.map { eventCardAccent(it).fill }.toSet()

            assertEquals("$group must share one container color", 1, containers.size)
            assertEquals("$group must share one fill color", 1, fills.size)

            containers.single() to fills.single()
        }

        assertEquals(groupColors.size, groupColors.toSet().size)
    }

    @Test
    fun dateBadgeAccent_isReadableBehindWhiteTextForEveryStatus() {
        EventBadgeStatus.entries.forEach { status ->
            val contrast = contrastRatio(eventCardAccent(status).container, Color.White)

            assertTrue("$status container contrast was $contrast", contrast >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun rendersTitleTimeLocationAndStatusLabel() {
        setCard(status = EventBadgeStatus.COMPLETED)

        composeTestRule.onNodeWithText("Design Systems Summit").assertIsDisplayed()
        composeTestRule.onNodeWithText("09:00 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manila Convention Center").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun rendersUppercasedMonthAndDayBadge() {
        setCard(day = "07", month = "apr")

        composeTestRule.onNodeWithText("07").assertIsDisplayed()
        composeTestRule.onNodeWithText("APR").assertIsDisplayed()
    }

    @Test
    fun blankDayAndMonth_renderPlaceholderTokens() {
        setCard(day = "", month = "")

        composeTestRule.onNodeWithText("--").assertIsDisplayed()
        composeTestRule.onNodeWithText("---").assertIsDisplayed()
    }

    @Test
    fun blankTitle_rendersUntitledEvent() {
        setCard(title = "")

        composeTestRule.onNodeWithText("Untitled Event").assertIsDisplayed()
    }

    @Test
    fun blankLocation_rendersVenueTBD() {
        setCard(location = "")

        composeTestRule.onNodeWithText("Venue TBD").assertIsDisplayed()
    }

    @Test
    fun unknownTimeSentinel_hidesTimeRow() {
        setCard(time = "-")

        composeTestRule.onNodeWithText("09:00 AM").assertDoesNotExist()
        composeTestRule.onNodeWithText("Manila Convention Center").assertIsDisplayed()
    }

    @Test
    fun blankTime_hidesTimeRow() {
        setCard(time = "")

        composeTestRule.onNodeWithText("09:00 AM").assertDoesNotExist()
    }

    @Test
    fun statusLabelOverride_replacesDefaultBadgeText() {
        setCard(status = EventBadgeStatus.PENDING, statusLabel = "Awaiting Review")

        composeTestRule.onNodeWithText("Awaiting Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
    }

    @Test
    fun nullOnClick_rendersNonClickableCard() {
        setCard(onClick = null)

        composeTestRule.onNodeWithText("Design Systems Summit").assertHasNoClickAction()
        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun nonNullOnClick_invokesCallback() {
        var clicks = 0
        setCard(onClick = { clicks++ })

        composeTestRule.onNode(hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun nonNullOnClick_targetsTheCardNotTheTitleText() {
        var clicks = 0
        setCard(onClick = { clicks++ })

        composeTestRule.onNodeWithText("Design Systems Summit").assertHasClickAction()
        composeTestRule.onNodeWithText("09:00 AM").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun registeredCountAndCapacity_renderProgressReadout() {
        setCard(registeredCount = 25, capacity = 50)

        composeTestRule.onNodeWithText("25 / 50 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun percentAboveCapacity_clampsToOneHundred() {
        setCard(registeredCount = 75, capacity = 50)

        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithText("75 / 50 Registered").assertIsDisplayed()
    }

    @Test
    fun zeroCapacity_hidesProgressReadout() {
        setCard(registeredCount = 5, capacity = 0)

        composeTestRule.onNodeWithText("5 / 0 Registered").assertDoesNotExist()
    }

    @Test
    fun nullRegisteredCount_hidesProgressReadout() {
        setCard(registeredCount = null, capacity = 50)

        composeTestRule.onNodeWithText("Registered").assertDoesNotExist()
    }

    @Test
    fun card_meetsMinimumHeight() {
        setCard(onClick = {})

        composeTestRule.onNode(hasClickAction()).assertHeightIsAtLeast(96.dp)
    }

    @Test
    fun trailingAction_doesNotBreakCardLayout() {
        setCard(onClick = {}, trailingAction = { Text(text = "OPEN") })

        composeTestRule.onNode(hasClickAction()).assertHeightIsAtLeast(96.dp)
        composeTestRule.onNodeWithText("OPEN").assertIsDisplayed()
    }

    @Test
    fun trailingActionSlot_isRendered() {
        setCard(trailingAction = { Text(text = "OPEN") })

        composeTestRule.onNodeWithText("OPEN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Design Systems Summit").assertIsDisplayed()
    }

    @Test
    fun everyStatus_rendersItsBadgeLabel() {
        composeTestRule.setContent {
            EventQrTheme {
                EventBadgeStatus.entries.forEach { status ->
                    EventCard(
                        title = "Card for ${status.name}",
                        status = status,
                        day = "01",
                        month = "feb",
                        time = "10:00 AM",
                        location = "Venue",
                    )
                }
            }
        }

        EventBadgeStatus.entries.forEach { status ->
            composeTestRule.onNodeWithText("Card for ${status.name}").assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approved").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rejected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
    }

    @Test
    fun completedCard_doesNotReuseLiveAccentColors() {
        val completed = eventCardAccent(EventBadgeStatus.COMPLETED)
        val active = eventCardAccent(EventBadgeStatus.ACTIVE)

        assertNotEquals(active.container, completed.container)
        assertNotEquals(active.fill, completed.fill)
    }

    @Test
    fun adapterSuppliedHumanizedLabels_renderTheirOwnTextNotTheRawEnum() {
        composeTestRule.setContent {
            EventQrTheme {
                EventCard(
                    title = "Draft card",
                    status = parseBadgeStatus("DRAFT"),
                    statusLabel = "Draft",
                    day = "01",
                    month = "feb",
                    time = "10:00 AM",
                    location = "Venue",
                )
                EventCard(
                    title = "Review card",
                    status = parseBadgeStatus("PENDING_REVIEW"),
                    statusLabel = "Pending Review",
                    day = "02",
                    month = "feb",
                    time = "11:00 AM",
                    location = "Venue",
                )
            }
        }

        composeTestRule.onNodeWithText("Draft card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("DRAFT").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h900dp-xhdpi")
    fun foldWidth_keepsEveryEventCardRegionDisplayed() {
        setCard(registeredCount = 25, capacity = 50)

        composeTestRule.onNodeWithText("Design Systems Summit").assertIsDisplayed()
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("MAR").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeTestRule.onNodeWithText("09:00 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manila Convention Center").assertIsDisplayed()
        composeTestRule.onNodeWithText("25 / 50 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun largeFontScale_smallViewport_keepsTitleBadgeAndProgressDisplayed() {
        setCard(registeredCount = 25, capacity = 50)

        composeTestRule.onNodeWithText("Design Systems Summit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manila Convention Center").assertIsDisplayed()
        composeTestRule.onNodeWithText("25 / 50 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun largeFontScale_cardStillMeetsItsMinimumHeight() {
        setCard(onClick = {})

        composeTestRule.onNode(hasClickAction()).assertHeightIsAtLeast(96.dp)
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
