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
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.R
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
    fun rejectionWithoutAnEventId_stillComposesARow() {
        setCard(item = notification(eventId = null, type = NotificationType.EVENT_REJECTED))

        composeTestRule.onNode(hasClickAction()).assertHasClickAction()
    }

    @Test
    fun approvedNotifications_useTheCheckMarkIcon() {
        var eventApprovedIcon = 0
        var scanApprovedIcon = 0

        composeTestRule.setContent {
            EventQrTheme {
                eventApprovedIcon = resolveNotificationIcon(NotificationType.EVENT_APPROVED)
                scanApprovedIcon = resolveNotificationIcon(NotificationType.SCAN_APPROVED)
            }
        }

        assertEquals(AppIcons.Success, eventApprovedIcon)
        assertEquals(AppIcons.Success, scanApprovedIcon)
    }
    private fun notification(
        status: NotificationStatus = NotificationStatus.PENDING,
        readAt: Instant? = null,
        eventId: UUID? = UUID.randomUUID(),
        type: NotificationType = NotificationType.REGISTRATION_NEW,
    ) = NotificationResponse(
        notificationId = UUID.randomUUID(),
        eventId = eventId,
        recipientUserId = UUID.randomUUID(),
        title = "Registration confirmed",
        message = "Your QR code is ready.",
        status = status,
        readAt = readAt,
        createdAt = Instant.now(),
        notificationType = type,
    )

    private fun setCard(item: NotificationResponse, onClick: () -> Unit = {}) {
        composeTestRule.setContent {
            EventQrTheme {
                NotificationRow(item = item, navigable = true, onClick = onClick)
            }
        }
    }
}


