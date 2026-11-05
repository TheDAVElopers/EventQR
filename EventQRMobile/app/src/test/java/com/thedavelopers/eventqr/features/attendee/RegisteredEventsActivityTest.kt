package com.thedavelopers.eventqr.features.attendee

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RegisteredEventsActivityTest {

    private val now: Instant = Instant.now()

    private fun registration(title: String, startOffsetHours: Long, endOffsetHours: Long) = RegistrationResponse(
        registrationId = UUID.randomUUID(), eventId = UUID.randomUUID(), attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com", attendeeName = "A", status = RegistrationStatus.REGISTERED,
        eventTitle = title, eventStartAt = now.plus(startOffsetHours, ChronoUnit.HOURS),
        eventEndAt = now.plus(endOffsetHours, ChronoUnit.HOURS),
    )

    private fun screen(): RegisteredEventsActivity {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()
        activity.showRegisteredEvents(
            listOf(
                registration("Coming Soon", 24, 27),
                registration("Happening Now", -1, 2),
                registration("Already Over", -48, -45),
            ),
        )
        return activity
    }

    private fun shown(activity: RegisteredEventsActivity): Int =
        activity.findViewById<RecyclerView>(R.id.recyclerRegisteredEvents).adapter!!.itemCount

    private fun chip(activity: RegisteredEventsActivity, id: Int): TextView = activity.findViewById(id)

    @Test
    fun chipsAreAllUpcomingActiveCompletedInThatOrder() {
        val activity = screen()

        val labels = listOf(R.id.chipAll, R.id.chipUpcoming, R.id.chipActive, R.id.chipCompleted)
            .map { chip(activity, it).text.toString() }

        assertEquals(listOf("All", "Upcoming", "Active", "Completed"), labels)
    }

    @Test
    fun eachChipShowsOnlyItsOwnEvents() {
        val activity = screen()

        assertEquals(3, shown(activity)) // All

        chip(activity, R.id.chipUpcoming).performClick()
        assertEquals(1, shown(activity))

        chip(activity, R.id.chipActive).performClick()
        assertEquals(1, shown(activity))

        chip(activity, R.id.chipCompleted).performClick()
        assertEquals(1, shown(activity))

        chip(activity, R.id.chipAll).performClick()
        assertEquals(3, shown(activity))
    }

    @Test
    fun selectedChipCarriesTheFillStateNotAnIcon() {
        val activity = screen()

        chip(activity, R.id.chipActive).performClick()

        assertEquals(true, chip(activity, R.id.chipActive).isSelected)
        assertEquals(false, chip(activity, R.id.chipAll).isSelected)
    }

    @Test
    fun anEmptyFilterSaysSoInsteadOfClaimingThereAreNoRegistrationsAtAll() {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()
        // Only a completed event: every filter except All and Completed is empty.
        activity.showRegisteredEvents(listOf(registration("Already Over", -48, -45)))
        val empty = activity.findViewById<com.thedavelopers.eventqr.ui.components.EventQrEmptyState>(R.id.txtRegisteredEventsEmpty)

        chip(activity, R.id.chipUpcoming).performClick()
        assertEquals("No upcoming registered events", empty.text.toString())
        assertEquals(android.view.View.VISIBLE, empty.visibility)

        chip(activity, R.id.chipActive).performClick()
        assertEquals("No active registered events", empty.text.toString())

        chip(activity, R.id.chipCompleted).performClick()
        assertEquals(android.view.View.GONE, empty.visibility)

        // With nothing registered at all, All keeps the original message.
        activity.showRegisteredEvents(emptyList())
        chip(activity, R.id.chipAll).performClick()
        assertEquals("No registered events yet", empty.text.toString())
        assertEquals(android.view.View.VISIBLE, empty.visibility)
    }
}
