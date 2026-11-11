package com.thedavelopers.eventqr.features.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * A shade tap marks the row read locally, and the shade copy only leaves once the server accepted
 * that mark: a failed mark must leave the notification in the shade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class NotificationMarkReadTest {

    private fun notification() = NotificationResponse(
        notificationId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        recipientUserId = UUID.randomUUID(),
        title = "Event request rejected",
        message = "Your event request was rejected.",
        status = NotificationStatus.PENDING,
        createdAt = Instant.now(),
        notificationType = NotificationType.EVENT_REJECTED,
    )

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `a shade tap marks the tapped row read in the in-memory list`() {
        val tapped = notification()
        val untouched = notification()

        val updated = withReadState(listOf(tapped, untouched), setOf(tapped.notificationId))

        assertEquals(NotificationStatus.READ, updated[0].status)
        assertNotNull(updated[0].readAt)
        assertEquals(NotificationStatus.PENDING, updated[1].status)
    }

    @Test
    fun `a failed mark read leaves the shade notification posted`() = runBlocking {
        val item = notification()
        val context: Context = ApplicationProvider.getApplicationContext()
        EventDecisionNotifier.post(context, item)
        assertEquals(1, shadowOf(manager(context)).allNotifications.size)

        EventDecisionNotifier.markRead(context, listOf(item.notificationId.toString())) {
            NetworkResult.Error("offline")
        }

        assertEquals(1, shadowOf(manager(context)).allNotifications.size)
    }

    @Test
    fun `an accepted mark read drops the shade notification`() = runBlocking {
        val item = notification()
        val context: Context = ApplicationProvider.getApplicationContext()
        EventDecisionNotifier.post(context, item)
        assertEquals(1, shadowOf(manager(context)).allNotifications.size)

        EventDecisionNotifier.markRead(context, listOf(item.notificationId.toString())) {
            NetworkResult.Success(Unit)
        }

        assertTrue(shadowOf(manager(context)).allNotifications.isEmpty())
    }
}
