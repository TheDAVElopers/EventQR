package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import org.junit.Assert.assertEquals
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.UUID

/** The header is a fixed "Event Details"; the event name belongs on the banner above the description. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventDetailActivityTest {

    private fun build(title: String?): EventDetailActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
        title?.let { intent.putExtra(EXTRA_EVENT_TITLE, it) }
        return Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()
    }

    private fun event(title: String) = AttendeeEventResponse(eventId = UUID.randomUUID(), title = title, description = "About the event")

    @Test
    fun whileLoading_headerSaysEventDetailsAndTheBannerShowsTheNameFromTheIntent() {
        val activity = build("Summer Fest")

        assertEquals("Event Details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("Summer Fest", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
    }

    @Test
    fun renderEvent_putsTheNameOnTheBannerAndLeavesTheHeaderAlone() {
        val activity = build(null)

        activity.renderEvent(event("Night Market"))

        assertEquals("Night Market", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
        assertEquals("Event Details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("About the event", activity.findViewById<TextView>(R.id.txtDetailDescription).text.toString())
    }

    @Test
    fun renderEvent_unlimitedCapacityIsLabelledUnlimitedNotDashes() {
        val activity = build(null)

        activity.renderEvent(event("Open Gate").copy(capacity = 0, currentAttendeeCount = 7))

        // renderEvent shows availability through the registration status block once the availability call returns;
        // the intent-extra path renders the same text immediately, so exercise that path too.
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
            .putExtra(EXTRA_EVENT_COUNT, "7").putExtra(EXTRA_EVENT_CAPACITY, "0")
        val fromIntent = Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()

        assertEquals("Unlimited (7 registered)", fromIntent.findViewById<TextView>(R.id.txtDetailCapacity).text.toString())
    }

    private fun availability(event: AttendeeEventResponse, count: Int, capacity: Int) = EventAvailabilityResponse(
        eventId = event.eventId, capacity = capacity, currentAttendeeCount = count,
        registrationOpen = true, full = false, available = true, message = "",
    )

    @Test
    fun availability_redrawsTheCapacityLineWithTheFreshCount() {
        // Launch snapshot from the list said 0 / 12; the availability call after registering says 1 / 12.
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
            .putExtra(EXTRA_EVENT_COUNT, "0").putExtra(EXTRA_EVENT_CAPACITY, "12")
        val activity = Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()
        val capacityView = activity.findViewById<TextView>(R.id.txtDetailCapacity)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 0)
        val before = capacityView.text.toString()

        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 1, capacity = 12))

        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), before)
        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 1, 12), capacityView.text.toString())
        assertEquals(
            activity.getString(R.string.event_detail_spots_remaining, 11),
            activity.findViewById<TextView>(R.id.txtRemainingSpots).text.toString(),
        )

        // After a cancel the next availability response drops the count back.
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), capacityView.text.toString())
    }

    @Test
    fun statusTurningUnregisteredAfterAvailability_restoresRegisterInsteadOfAlreadyRegistered() {
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 1)

        // Availability lands while the (stale) status still says registered.
        activity.updateRegistrationStatus(true)
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_already_registered), button.text.toString())

        // The fresh status (cancelled elsewhere) arrives afterwards.
        activity.updateRegistrationStatus(false)

        assertEquals(activity.getString(R.string.event_detail_register), button.text.toString())
        assertEquals(true, button.isEnabled)
    }

    @Test
    fun unregisteredStatusBeforeAnyAvailability_leavesTheButtonForTheAvailabilityCall() {
        val activity = build(null)
        val fest = event("Fest").copy(capacity = 12)

        activity.updateRegistrationStatus(false)

        assertEquals(activity.getString(R.string.event_detail_loading), activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent).text.toString())
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_register), activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent).text.toString())
    }

    @Test
    fun cancelThenAvailability_dropsTheCount() {
        val activity = build(null)
        val capacityView = activity.findViewById<TextView>(R.id.txtDetailCapacity)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 1)
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), capacityView.text.toString())
    }

    /** Each availability call suspends on its own deferred, so tests decide the order responses land in. */
    private class QueuedAvailabilityRepo : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val pending = mutableListOf<CompletableDeferred<NetworkResult<EventAvailabilityResponse>>>()
        override suspend fun getEventAvailability(eventId: String): NetworkResult<EventAvailabilityResponse> =
            CompletableDeferred<NetworkResult<EventAvailabilityResponse>>().also { pending += it }.await()
    }

    @Test
    fun olderAvailabilityResponseLandingLast_isDroppedAfterACancel() {
        val activity = build(null)
        val repo = QueuedAvailabilityRepo()
        activity.repository = repo
        val capacityView = activity.findViewById<TextView>(R.id.txtDetailCapacity)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 1)

        activity.renderEvent(fest) // first availability request (pre-cancel, slow)
        shadowOf(Looper.getMainLooper()).idle()
        activity.onRegistrationCancelled("Registration cancelled") // second request supersedes it
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, repo.pending.size)

        repo.pending[1].complete(NetworkResult.Success(availability(fest, count = 0, capacity = 12)))
        shadowOf(Looper.getMainLooper()).idle()
        repo.pending[0].complete(NetworkResult.Success(availability(fest, count = 1, capacity = 12)))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), capacityView.text.toString())
        assertEquals(
            activity.getString(R.string.event_detail_register),
            activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent).text.toString(),
        )
    }
}
