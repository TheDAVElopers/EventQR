package com.thedavelopers.eventqr.features.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.MyEventRequestsActivity
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The backend sends `eventId: null` for a decision on a request that never became an event. A
 * rejection still opens the requester's own list, where `adminRemarks` is readable; an approval has
 * nowhere to go, so it is never posted. No intent may ever carry the literal event id "null".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class NotificationNullEventIdTest {

    private fun decision(type: String) = """
        {
          "notificationId": "3f0a5c9e-3d0a-4c55-9a3e-0b8f6d1c2a10",
          "eventId": null,
          "recipientUserId": "8b0f7a2c-6a49-4f4b-9d3e-9a1c2b3d4e5f",
          "title": "Event request $type",
          "message": "Your event request was $type.",
          "status": "PENDING",
          "notificationType": "EVENT_$type"
        }
    """.trimIndent()

    private fun rejection(): NotificationResponse =
        sharedGson().fromJson(decision("REJECTED"), NotificationResponse::class.java)

    private fun approval(): NotificationResponse =
        sharedGson().fromJson(decision("APPROVED"), NotificationResponse::class.java)

    @Test
    fun `a rejection without an event id routes to the requester's own list`() {
        val item = rejection()

        assertNull(item.eventId)
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            notificationDestinationFor(item.notificationType, item.eventId?.toString()),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            resolvedDestination(item.notificationType, item.eventId?.toString(), organizer = false),
        )
        assertEquals(
            NotificationDestination.ATTENDEE_REQUESTS,
            resolvedDestination(item.notificationType, item.eventId?.toString(), organizer = true),
        )
    }

    @Test
    fun `the notifier posts a rejection without an event id once and opens the request list`() {
        val item = rejection()
        val context: Context = ApplicationProvider.getApplicationContext()

        assertTrue(EventDecisionNotifier.shouldPost(item, emptySet()))
        assertFalse(EventDecisionNotifier.shouldPost(item, setOf(item.notificationId.toString())))
        EventDecisionNotifier.post(context, item)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).allNotifications.single()
        val intent = shadowOf(posted.contentIntent).savedIntent

        assertEquals(MyEventRequestsActivity::class.java.name, intent.component?.className)
        assertNull(intent.getStringExtra("event_id"))
    }

    @Test
    fun `the notifier skips an approval without an event id`() {
        val item = approval()
        val context: Context = ApplicationProvider.getApplicationContext()

        assertFalse(EventDecisionNotifier.shouldPost(item, emptySet()))
        EventDecisionNotifier.post(context, item)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `an ordinary decision is still posted exactly once`() {
        val item = rejection().copy(eventId = UUID.randomUUID())

        assertTrue(EventDecisionNotifier.shouldPost(item, emptySet()))
        assertFalse(EventDecisionNotifier.shouldPost(item, setOf(item.notificationId.toString())))
    }
}
