package com.thedavelopers.eventqr.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventRequestCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fixedInstant: Instant = Instant.parse("2026-03-14T05:30:00Z")

    @Test
    fun rendersEventNameAndStatusBadge() {
        setCard(eventName = "Tech Fest 2026", status = EventRequestStatus.PENDING)

        composeTestRule.onNodeWithText("Tech Fest 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun rendersFormattedSubmittedDate() {
        setCard(createdAt = fixedInstant)

        val expected = "Submitted ${DateFormatters.formatEventDate(fixedInstant)}"
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
        assertEquals("Mar 14, 2026", DateFormatters.eventDateFormatter.format(fixedInstant))
    }

    @Test
    fun nullCreatedAt_rendersDashFallback() {
        setCard(createdAt = null)

        composeTestRule.onNodeWithText("Submitted --").assertIsDisplayed()
    }

    @Test
    fun blankEventName_rendersUntitledEvent() {
        setCard(eventName = "   ")

        composeTestRule.onNodeWithText("Untitled Event").assertIsDisplayed()
    }

    @Test
    fun mapsAllRequestStatusesToBadgeLabels() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventRequestStatus.entries.forEach { status ->
                    EventRequestCard(
                        request = request(eventName = "Request ${status.name}", status = status),
                        onClick = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Request APPROVED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Request REJECTED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Request PENDING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approved").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rejected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun card_meetsMinimumHeight() {
        setCard()

        composeTestRule.onNodeWithText("Tech Fest 2026").assertHeightIsAtLeast(72.dp)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h900dp-xhdpi")
    fun foldWidth_keepsEventNameBadgeAndSubmittedLineDisplayed() {
        setCard()

        composeTestRule.onNodeWithText("Tech Fest 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Submitted ${DateFormatters.formatEventDate(fixedInstant)}")
            .assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun largeFontScale_smallViewport_keepsEventNameAndBadgeDisplayed() {
        setCard(status = EventRequestStatus.APPROVED)

        composeTestRule.onNodeWithText("Tech Fest 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Approved").assertIsDisplayed()
    }

    private fun setCard(
        eventName: String = "Tech Fest 2026",
        status: EventRequestStatus = EventRequestStatus.PENDING,
        createdAt: Instant? = fixedInstant,
    ) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventRequestCard(
                    request = request(eventName = eventName, status = status, createdAt = createdAt),
                    onClick = {},
                )
            }
        }
    }

    private fun request(
        eventName: String,
        status: EventRequestStatus,
        createdAt: Instant? = fixedInstant,
    ): EventRequestResponse = EventRequestResponse(
        eventRequestId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        requesterUserId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        eventName = eventName,
        status = status,
        createdAt = createdAt,
    )
}
