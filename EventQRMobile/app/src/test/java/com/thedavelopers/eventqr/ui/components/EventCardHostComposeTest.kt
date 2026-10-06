package com.thedavelopers.eventqr.ui.components

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventCardHostComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTitleLocationAndBadgeLabel() {
        setHost(
            EventCardState(
                title = "Campus Tech Fest",
                status = "COMPLETED",
                day = "09",
                month = "Sep",
                time = "08:30 AM",
                location = "Ateneo Grounds",
            ),
        )

        composeTestRule.onNodeWithText("Campus Tech Fest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ateneo Grounds").assertIsDisplayed()
        composeTestRule.onNodeWithText("08:30 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("09").assertIsDisplayed()
        composeTestRule.onNodeWithText("SEP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun rawEnumStatus_isNotRenderedVerbatimAsBadgeLabel() {
        setHost(EventCardState(title = "Campus Tech Fest", status = "PENDING_REVIEW"))

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENDING_REVIEW").assertDoesNotExist()
    }

    @Test
    fun rawApprovedEnumStatus_rendersHumanReadableApprovedLabel() {
        setHost(EventCardState(title = "Campus Tech Fest", status = "APPROVED"))

        composeTestRule.onNodeWithText("Approved").assertIsDisplayed()
        composeTestRule.onNodeWithText("APPROVED").assertDoesNotExist()
    }

    @Test
    fun rawRegisteredEnumStatus_rendersHumanReadableRegisteredLabel() {
        setHost(EventCardState(title = "Campus Tech Fest", status = "REGISTERED"))

        composeTestRule.onNodeWithText("Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("REGISTERED").assertDoesNotExist()
    }

    @Test
    fun unrecognizedStatus_rendersUnknownLabelWithoutLeakingRawString() {
        setHost(EventCardState(title = "Campus Tech Fest", status = "NOT_A_STATUS"))

        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
        composeTestRule.onNodeWithText("NOT_A_STATUS").assertDoesNotExist()
        composeTestRule.onNodeWithText("Campus Tech Fest").assertIsDisplayed()
    }

    @Test
    fun draftStatus_rendersDraftLabelNotPending() {
        setHost(EventCardState(title = "Campus Tech Fest", status = "Draft"))

        composeTestRule.onNodeWithText("Draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
    }

    @Test
    fun unrecognisedStylerLabel_rendersUnknownLabel() {
        setHost(EventCardState(title = "Campus Tech Fest", status = "Status: BRAND_NEW"))

        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
    }

    @Test
    fun blankStatus_rendersUnknownBadgeLabel() {
        setHost(EventCardState(title = "Campus Tech Fest", status = ""))

        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Campus Tech Fest").assertIsDisplayed()
    }

    @Test
    fun registeredCountAndCapacity_renderProgressReadout() {
        setHost(EventCardState(title = "Campus Tech Fest", registeredCount = 30, capacity = 60))

        composeTestRule.onNodeWithText("30 / 60 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun defaultState_rendersUnknownDatePlaceholders() {
        setHost(EventCardState())

        composeTestRule.onNodeWithText("--").assertIsDisplayed()
        composeTestRule.onNodeWithText("---").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 / 1 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun defaultState_hidesUnknownTimeSentinel() {
        setHost(EventCardState(title = "Campus Tech Fest"))

        composeTestRule.onNodeWithText("-").assertDoesNotExist()
    }

    @Test
    fun nullOnClick_rendersNonClickableCard() {
        setHost(EventCardState(title = "Campus Tech Fest", onClick = null))

        composeTestRule.onNodeWithText("Campus Tech Fest").assertHasNoClickAction()
        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun nonNullOnClick_invokesCallback() {
        var clicks = 0
        setHost(EventCardState(title = "Campus Tech Fest", onClick = { clicks++ }))

        composeTestRule.onNode(hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun zeroCapacity_isCoercedToOneAndStillRendersProgress() {
        setHost(EventCardState(title = "Campus Tech Fest", registeredCount = 4, capacity = 0))

        composeTestRule.onNodeWithText("4 / 1 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun negativeCapacity_isCoercedToOneAndStillRendersProgress() {
        setHost(EventCardState(title = "Campus Tech Fest", registeredCount = 3, capacity = -5))

        composeTestRule.onNodeWithText("3 / 1 Registered").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
    }

    private fun setHost(state: EventCardState) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventCardHost(state)
            }
        }
    }
}