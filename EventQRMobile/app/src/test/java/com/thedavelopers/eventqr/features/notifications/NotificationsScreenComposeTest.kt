package com.thedavelopers.eventqr.features.notifications

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreen
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenBg
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationsScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unreadNotification_announcesUnreadStateAndTitle() {
        setCard(item = notification(status = NotificationStatus.PENDING))

        composeTestRule
            .onNodeWithContentDescription("Unread notification. Registration confirmed")
            .assertIsDisplayed()
    }

    @Test
    fun readNotification_announcesReadStateAndTitle() {
        setCard(item = notification(status = NotificationStatus.READ))

        composeTestRule
            .onNodeWithContentDescription("Read notification. Registration confirmed")
            .assertIsDisplayed()
    }

    @Test
    fun readAtTimestamp_marksNotificationReadRegardlessOfStatus() {
        setCard(item = notification(status = NotificationStatus.PENDING, readAt = Instant.now()))

        composeTestRule
            .onNodeWithContentDescription("Read notification. Registration confirmed")
            .assertIsDisplayed()
    }

    @Test
    fun unreadNotification_doesNotClaimToBeRead() {
        setCard(item = notification(status = NotificationStatus.PENDING))

        composeTestRule
            .onNodeWithContentDescription("Read notification. Registration confirmed")
            .assertDoesNotExist()
    }

    @Test
    fun notificationCard_invokesClickCallback() {
        var clicks = 0
        setCard(item = notification(), onClick = { clicks++ })

        composeTestRule.onNode(hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun notificationCard_keepsTitleVisibleAlongsideStateDescription() {
        setCard(item = notification())

        composeTestRule.onNodeWithText("Registration confirmed").assertIsDisplayed()
        composeTestRule.onNode(hasClickAction()).assertHasClickAction()
    }

    @Test
    fun approvedNotifications_useActiveGreenCheckMarkVisuals() {
        lateinit var eventApprovedVisuals: Triple<ImageVector, Color, Color>
        lateinit var scanApprovedVisuals: Triple<ImageVector, Color, Color>

        composeTestRule.setContent {
            EventQrTheme {
                eventApprovedVisuals = resolveNotificationVisuals(NotificationType.EVENT_APPROVED)
                scanApprovedVisuals = resolveNotificationVisuals(NotificationType.SCAN_APPROVED)
            }
        }

        assertEquals(Icons.Default.CheckCircle, eventApprovedVisuals.first)
        assertEquals(StatusActiveGreen, eventApprovedVisuals.second)
        assertEquals(StatusActiveGreenBg, eventApprovedVisuals.third)

        assertEquals(Icons.Default.CheckCircle, scanApprovedVisuals.first)
        assertEquals(StatusActiveGreen, scanApprovedVisuals.second)
        assertEquals(StatusActiveGreenBg, scanApprovedVisuals.third)
    }

    private fun notification(
        status: NotificationStatus = NotificationStatus.PENDING,
        readAt: Instant? = null,
    ) = NotificationResponse(
        notificationId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        recipientUserId = UUID.randomUUID(),
        title = "Registration confirmed",
        message = "Your QR code is ready.",
        status = status,
        readAt = readAt,
        createdAt = Instant.now(),
        notificationType = NotificationType.REGISTRATION_NEW,
    )

    private fun setCard(item: NotificationResponse, onClick: () -> Unit = {}) {
        composeTestRule.setContent {
            EventQrTheme {
                NotificationItemCard(item = item, onClick = onClick)
            }
        }
    }
}
