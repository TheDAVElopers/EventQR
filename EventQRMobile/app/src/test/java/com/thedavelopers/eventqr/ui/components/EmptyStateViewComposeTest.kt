package com.thedavelopers.eventqr.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EmptyStateViewComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTitleAndDescription() {
        setEmptyState()

        composeTestRule.onNodeWithText("No events yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create your first event to get started.").assertIsDisplayed()
    }

    @Test
    fun actionLabelWithoutCallback_rendersNoButton() {
        setEmptyState(actionLabel = "Create Event", onActionClick = null)

        composeTestRule.onNodeWithText("No events yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Event").assertDoesNotExist()
        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun blankActionLabelWithoutCallback_rendersNoButton() {
        setEmptyState(actionLabel = "   ", onActionClick = null)

        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun actionLabelWithCallback_rendersButtonAndFires() {
        var clicks = 0
        setEmptyState(actionLabel = "Create Event", onActionClick = { clicks++ })

        composeTestRule.onNodeWithText("Create Event").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun callbackWithoutActionLabel_rendersNoButton() {
        var clicks = 0
        setEmptyState(actionLabel = null, onActionClick = { clicks++ })

        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
        assertEquals(0, clicks)
    }

    @Test
    fun blankActionLabelWithCallback_rendersNoButton() {
        setEmptyState(actionLabel = "", onActionClick = {})

        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun actionButton_meetsMinimumHeight() {
        setEmptyState(actionLabel = "Create Event", onActionClick = {})

        composeTestRule.onNode(hasText("Create Event") and hasClickAction()).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun nullActionLabel_rendersNoButton() {
        setEmptyState(actionLabel = null, onActionClick = null)

        composeTestRule.onNodeWithText("No events yet").assertIsDisplayed()
        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    private fun setEmptyState(actionLabel: String? = null, onActionClick: (() -> Unit)? = null) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EmptyStateView(
                    icon = Icons.Default.Info,
                    title = "No events yet",
                    description = "Create your first event to get started.",
                    actionLabel = actionLabel,
                    onActionClick = onActionClick,
                )
            }
        }
    }
}
