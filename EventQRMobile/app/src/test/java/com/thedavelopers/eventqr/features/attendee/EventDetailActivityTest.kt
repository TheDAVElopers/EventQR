package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** The header is a fixed "Event details"; the event name belongs on the banner above the description. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventDetailActivityTest {

    private fun build(title: String?): EventDetailActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
        title?.let { intent.putExtra(EXTRA_EVENT_TITLE, it) }
        return Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()
    }

    private fun event(title: String) = AttendeeEventResponse(eventId = UUID.randomUUID(), title = title, description = "About the event")

    @After
    fun resetFontScale() {
        RuntimeEnvironment.setFontScale(1.0f)
    }

    /**
     * A font-scale change recreates the activity (no `configChanges` in the manifest), so the scale
     * must be set before the activity is built. Availability is queued, never fetched.
     */
    private fun buildWithRegWindow(fontScale: Float): EventDetailActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val activity = build(null)
        activity.repository = QueuedAvailabilityRepo()
        activity.renderEvent(
            event("Windowed").copy(
                registrationOpenAt = Instant.now().plusSeconds(86_400),
                registrationCloseAt = Instant.now().plusSeconds(172_800),
            ),
        )
        return activity
    }

    @Test
    fun regWindowRow_aboveTheFontScaleThreshold_stacksTheValueUnderTheLabel() {
        val activity = buildWithRegWindow(2.0f)
        val row = activity.findViewById<LinearLayout>(R.id.layoutRegWindowRow)
        val value = activity.findViewById<TextView>(R.id.txtDetailRegWindow)
        val params = value.layoutParams as LinearLayout.LayoutParams

        assertEquals(LinearLayout.VERTICAL, row.orientation)
        assertEquals(Gravity.START, value.gravity and Gravity.START)
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(0f, params.weight, 0f)
        assertTrue(params.topMargin > 0)
    }

    @Test
    fun regWindowRow_atTheDefaultFontScale_keepsTheSharedRow() {
        val activity = buildWithRegWindow(1.0f)
        val row = activity.findViewById<LinearLayout>(R.id.layoutRegWindowRow)
        val params = activity.findViewById<TextView>(R.id.txtDetailRegWindow).layoutParams as LinearLayout.LayoutParams

        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        assertEquals(0, params.width)
        assertEquals(1f, params.weight, 0f)
    }

    @Test
    fun whileLoading_headerSaysEventDetailsAndTheBannerShowsTheNameFromTheIntent() {
        val activity = build("Summer Fest")

        assertEquals("Event details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("Summer Fest", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
    }

    @Test
    fun renderEvent_putsTheNameOnTheBannerAndLeavesTheHeaderAlone() {
        val activity = build(null)

        activity.renderEvent(event("Night Market"))

        assertEquals("Night Market", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
        assertEquals("Event details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
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

    // A registered attendee's primary action is the pass (§6.2). The pass moved here when the
    // registered-events list stopped carrying a per-row button.
    @Test
    fun registeredAttendee_opensThePassFromThePrimaryAction() {
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.updateRegistrationStatus(true)
        assertEquals(activity.getString(R.string.event_detail_already_registered), button.text.toString())

        activity.setRegisteredId("registration-1")

        assertEquals(activity.getString(R.string.ui_attendee_view_pass), button.text.toString())
        assertEquals(true, button.isEnabled)

        button.performClick()
        assertEquals(
            QrDisplayActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    // A checked-in (ENTERED) attendee still owns a pass: the pass id is the live registration id,
    // not the stricter cancellable id, which only a REGISTERED row with no recorded activity yields.
    @Test
    fun checkedInAttendee_stillGetsAnEnabledViewPass() {
        val activity = build(null)
        val future = event("Checked in").copy(eventStartAt = Instant.now().plusSeconds(86_400))
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(future)
        activity.updateRegistrationStatus(true)
        activity.setRegisteredId("registration-entered")

        assertEquals(activity.getString(R.string.ui_attendee_view_pass), button.text.toString())
        assertEquals(true, button.isEnabled)

        button.performClick()
        assertEquals(
            QrDisplayActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
        // Cancel keeps its own gate: no verified cancellable id, no cancel action.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnCancelRegistration).visibility)
    }

    @Test
    fun cancellableIdAlone_neverDrivesThePassLabel() {
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.updateRegistrationStatus(true)
        activity.setCancellableRegistration("registration-1")

        assertEquals(activity.getString(R.string.event_detail_already_registered), button.text.toString())
        assertEquals(false, button.isEnabled)
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

    // Audit X6: an approved attendee-owned event must not offer "Manage Event" — every organizer
    // management endpoint rejects an ATTENDEE, so the hub would only show a 403 error state.
    @Test
    fun attendeeOwnedEvent_offersRegisterNotManageEvent() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ATTENDEE)
        val activity = build(null)
        activity.repository = QueuedAvailabilityRepo()
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(event("Owned by me").copy(isOwnedByCurrentUser = true))
        shadowOf(Looper.getMainLooper()).idle()

        assertNotEquals(activity.getString(R.string.event_detail_manage_event), button.text.toString())
    }

    @Test
    fun organizerOwnedEvent_stillOffersManageEvent() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ORGANIZER)
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(event("Owned by me").copy(isOwnedByCurrentUser = true))

        assertEquals(activity.getString(R.string.event_detail_manage_event), button.text.toString())
    }

    // Precedence: an organizer who also holds a live registration (role promotion, ownership
    // transfer) manages the event. The registration status and its id must not repaint the label
    // or redirect the click to an attendee pass.
    @Test
    fun managerWithALiveRegistration_keepsManageEventAndItsDestination() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ORGANIZER)
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(event("Owned by me").copy(isOwnedByCurrentUser = true))
        activity.updateRegistrationStatus(true)
        activity.setRegisteredId("registration-1")

        assertEquals(activity.getString(R.string.event_detail_manage_event), button.text.toString())
        button.performClick()
        assertEquals(
            EventManagementHubActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    // An attendee-owned event offers Register, so it must also offer Cancel once registered,
    // otherwise the attendee is stuck in a registration they cannot drop.
    @Test
    fun attendeeOwnedEvent_showsCancelRegistrationOnceRegistered() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ATTENDEE)
        val activity = build(null)
        activity.repository = QueuedAvailabilityRepo()
        val future = event("Owned by me").copy(
            isOwnedByCurrentUser = true,
            eventStartAt = Instant.now().plusSeconds(86_400),
        )

        activity.renderEvent(future)
        activity.setCancellableRegistration("registration-1")

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnCancelRegistration).visibility)
    }

    @Test
    fun organizerOwnedEvent_hidesCancelRegistration() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ORGANIZER)
        val activity = build(null)
        val future = event("Owned by me").copy(
            isOwnedByCurrentUser = true,
            eventStartAt = Instant.now().plusSeconds(86_400),
        )

        activity.renderEvent(future)
        activity.setCancellableRegistration("registration-1")

        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnCancelRegistration).visibility)
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
