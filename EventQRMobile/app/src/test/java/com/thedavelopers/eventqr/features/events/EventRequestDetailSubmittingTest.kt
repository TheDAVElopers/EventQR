package com.thedavelopers.eventqr.features.events

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "One decision, one submit" (§6.5): while a decision is in flight both footer controls are
 * disabled, and they come back the moment it settles. `isLoading` alone is not the guard - a pull
 * to refresh over a loaded request is `isLoading` too - so the test flips it under the same request.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventRequestDetailSubmittingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val request = EventRequestResponse(
        eventRequestId = UUID.randomUUID(),
        requesterUserId = UUID.randomUUID(),
        eventName = "DevFest Manila",
        status = EventRequestStatus.PENDING,
    )

    @Test
    fun footerIsDisabledOnlyWhileADecisionIsInFlight() {
        val loading = mutableStateOf(true)
        composeTestRule.setContent {
            EventQrTheme {
                EventRequestDetailScreen(
                    request = request,
                    isLoading = loading.value,
                    errorMessage = null,
                    isAdmin = true,
                    onRetryClick = {},
                    onApproveClick = {},
                    onRejectClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Approve").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Reject").assertIsNotEnabled()

        loading.value = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Approve").assertIsEnabled()
        composeTestRule.onNodeWithText("Reject").assertIsEnabled()
    }
}
