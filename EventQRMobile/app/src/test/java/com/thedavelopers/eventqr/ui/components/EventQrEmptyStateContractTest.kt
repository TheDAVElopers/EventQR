package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Locks the two rules that produced the "No attendees found over the search bar" bug:
 * 1. `setContent` never changes visibility; only the host decides when the slot shows.
 * 2. An empty state shares its slot with the list, so it can never be laid out over the header.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventQrEmptyStateContractTest {

    private fun themedContext(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    @Test
    fun setContentKeepsVisibilityForTheHost_showReveals_hideHides() {
        val state = EventQrEmptyState(themedContext())
        // The host hides the slot in XML before the first render.
        state.visibility = View.GONE

        state.setContent("No attendees found.", "Registered attendees will show up here.")
        assertEquals(View.GONE, state.visibility)
        assertEquals("No attendees found.", state.text.toString())

        state.show("No attendees found.")
        assertEquals(View.VISIBLE, state.visibility)

        state.hide()
        assertEquals(View.GONE, state.visibility)
    }

    @Test
    fun actionKeepsThe48dpTouchFloor() {
        val context = themedContext()
        val state = EventQrEmptyState(context)
        state.setAction("Retry") {}

        val action = state.findViewById<View>(R.id.emptyAction)
        val floor = (48 * context.resources.displayMetrics.density).toInt()
        val children = (0 until state.childCount).joinToString { "${state.getChildAt(it)::class.java.simpleName}:${state.getChildAt(it).minimumHeight}" }
        org.junit.Assert.assertTrue(
            "empty-state action must be at least 48dp tall: children=[$children] floor=$floor",
            action.minimumHeight >= floor,
        )
    }

    @Test
    fun xmlSubtitleSurvivesATextAssignment() {
        val context = themedContext()
        val root = LayoutInflater.from(context).inflate(R.layout.activity_event_registrations, null, false)
        val empty = root.findViewById<EventQrEmptyState>(R.id.txtAssignedEventsEmpty)
        val subtitle = empty.subtext

        // A render path replaces the headline after the layout already supplied the body copy.
        empty.text = context.getString(R.string.event_registrations_no_attendees_found)

        assertEquals(context.getString(R.string.event_registrations_no_attendees_found), empty.title.toString())
        assertEquals(subtitle.toString(), empty.subtext.toString())
        assertEquals(View.VISIBLE, empty.getChildAt(2).visibility)
    }

    @Test
    fun hostThatPadsItsOwnContainerDoesNotDoubleTheGutter() {
        val context = themedContext()
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)

        // The component owns the gutter where the host does not pad.
        val owned = LayoutInflater.from(context).inflate(R.layout.activity_organizer_events, null, false)
            .findViewById<EventQrEmptyState>(R.id.layoutEventsEmpty)
        assertEquals(gutter, owned.paddingLeft)

        // The four hosts that pad their list container opt out, so the copy keeps one 20dp edge.
        val hosted = listOf(
            R.layout.activity_my_event_requests to R.id.txtMyRequestsEmpty,
            R.layout.activity_user_transaction_history to R.id.txtTransactionsEmpty,
            R.layout.activity_user_rewards to R.id.layoutNoRegisteredEvents,
            R.layout.activity_user_rewards to R.id.layoutRewardsEmpty,
        )
        for ((layout, id) in hosted) {
            val state = LayoutInflater.from(context).inflate(layout, null, false).findViewById<EventQrEmptyState>(id)
            assertEquals("${context.resources.getResourceEntryName(id)} must not add a second gutter", 0, state.paddingLeft)
        }

        // Claimed rewards owns its gutter: the list rows carry the 20dp inset, so the host does not pad.
        val claimed = LayoutInflater.from(context).inflate(R.layout.activity_claimed_rewards, null, false)
            .findViewById<EventQrEmptyState>(R.id.txtClaimedRewardsEmpty)
        assertEquals("claimed rewards empty state owns the gutter", gutter, claimed.paddingLeft)
    }

    @Test
    fun eventRegistrations_emptyStateSharesTheListSlot_notTheHeader() {
        val context = themedContext()
        val root = LayoutInflater.from(context).inflate(R.layout.activity_event_registrations, null, false)

        val empty = root.findViewById<View>(R.id.txtAssignedEventsEmpty)
        val list = root.findViewById<View>(R.id.recyclerEventRegistrations)
        val search = root.findViewById<View>(R.id.edtRegistrationSearch)

        assertSame("empty state must sit in the list slot", list.parent, empty.parent)
        assertNotSame("empty state must not be laid out with the search bar", search.parent, empty.parent)
    }
}
