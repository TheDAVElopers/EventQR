package com.thedavelopers.eventqr.features.attendee

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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

    // Two equal-width rows of two: every filter reachable at large font scale, no horizontal
    // scroller hiding two of four chips off the edge.
    @Test
    fun theChipRowIsATwoByTwoGridOfEqualCells() {
        val activity = screen()
        val grid = activity.findViewById<LinearLayout>(R.id.chipRowRegisteredEvents)

        assertEquals(LinearLayout.VERTICAL, grid.orientation)
        assertEquals(2, grid.childCount)

        val chipIds = listOf(R.id.chipAll, R.id.chipUpcoming, R.id.chipActive, R.id.chipCompleted)
        val touchFloor = activity.resources.getDimensionPixelSize(R.dimen.ui_touch_min)
        chipIds.forEach { id ->
            val chip = chip(activity, id)
            assertEquals("$id must wrap its label", ViewGroup.LayoutParams.WRAP_CONTENT, chip.layoutParams.height)
            assertEquals("$id keeps the 48dp floor", touchFloor, chip.minHeight)
        }

        for (rowIndex in 0 until grid.childCount) {
            val line = grid.getChildAt(rowIndex) as LinearLayout
            assertEquals(LinearLayout.HORIZONTAL, line.orientation)
            assertEquals(2, line.childCount)
            for (cell in 0 until line.childCount) {
                val params = line.getChildAt(cell).layoutParams as LinearLayout.LayoutParams
                assertEquals(0, params.width)
                assertEquals(1f, params.weight)
            }
        }
    }

    @Test
    fun selectedChipCarriesTheFillStateNotAnIcon() {
        val activity = screen()

        chip(activity, R.id.chipActive).performClick()

        assertEquals(true, chip(activity, R.id.chipActive).isSelected)
        assertEquals(false, chip(activity, R.id.chipAll).isSelected)
    }

    @Test
    fun aFailedEmptyLoadShowsTheRetryStateInsteadOfABlankList() {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()
        val empty = activity.findViewById<com.thedavelopers.eventqr.ui.components.EventQrEmptyState>(R.id.txtRegisteredEventsEmpty)

        // First load: the skeleton owns the area and the empty state is hidden, so a failed load
        // must restore an honest state instead of leaving the region blank.
        activity.showLoading(true)
        activity.showLoading(false)
        activity.showMessage("Couldn't reach the server.")

        assertEquals(android.view.View.VISIBLE, empty.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_registrations_error_title), empty.title.toString())
        val retry = empty.findViewById<android.view.View>(R.id.emptyAction)
        assertEquals(android.view.View.VISIBLE, retry.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_retry), (retry as TextView).text.toString())

        // A successful reload restores the pass empty state; the failed variant never survives it.
        activity.showRegisteredEvents(listOf(registration("Coming Soon", 24, 27)))
        assertEquals(android.view.View.GONE, retry.visibility)
        assertEquals(1, shown(activity))
    }

    // The error state owns the only Retry on a zero-row failure: a chip tap must not swap it for the
    // filter-empty copy, which offers no way back.
    @Test
    fun chipTapKeepsTheRetryWhileTheLoadHasFailedWithNoRows() {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()
        val empty = activity.findViewById<com.thedavelopers.eventqr.ui.components.EventQrEmptyState>(R.id.txtRegisteredEventsEmpty)
        activity.showLoading(true)
        activity.showLoading(false)
        activity.showMessage("Couldn't reach the server.")
        val retry = empty.findViewById<android.view.View>(R.id.emptyAction)

        chip(activity, R.id.chipCompleted).performClick()

        assertEquals(android.view.View.VISIBLE, empty.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_registrations_error_title), empty.title.toString())
        assertEquals(android.view.View.VISIBLE, retry.visibility)
        assertEquals(false, chip(activity, R.id.chipCompleted).isSelected)
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

    // First load is the skeleton, never a blank list area and never the pass empty state.
    @Test
    fun firstLoadShowsTheSkeletonInsteadOfTheEmptyState() {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()

        activity.showLoading(true)

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.skeletonLoading).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtRegisteredEventsEmpty).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.recyclerRegisteredEvents).visibility)
    }

    // A successful empty load owns the pass empty state, and a filter row over no passes is noise.
    @Test
    fun anEmptyLoadRevealsThePassEmptyStateAndHidesTheFilters() {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()
        activity.showLoading(true)

        activity.showRegisteredEvents(emptyList())

        assertEquals(View.GONE, activity.findViewById<View>(R.id.skeletonLoading).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.txtRegisteredEventsEmpty).visibility)
        assertEquals(0, shown(activity))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.chipRowRegisteredEvents).visibility)
    }

    // The chips only hide when there is nothing to filter; a filter that matches nothing keeps
    // them so the user can switch back.
    @Test
    fun aFilterThatMatchesNothingKeepsTheChipRow() {
        val activity = Robolectric.buildActivity(RegisteredEventsActivity::class.java).create().get()
        activity.showRegisteredEvents(listOf(registration("Already Over", -48, -45)))
        val chips = activity.findViewById<View>(R.id.chipRowRegisteredEvents)

        assertEquals(View.VISIBLE, chips.visibility)

        chip(activity, R.id.chipUpcoming).performClick()

        assertEquals(View.VISIBLE, chips.visibility)
        assertEquals(0, shown(activity))
    }
}
