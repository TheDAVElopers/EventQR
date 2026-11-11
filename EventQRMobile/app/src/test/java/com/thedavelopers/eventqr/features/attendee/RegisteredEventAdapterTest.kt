package com.thedavelopers.eventqr.features.attendee

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.util.EventCardPresenter
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.RegisteredEventAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * My passes rows are `list_row_date` at Discover density (§5.11): title, "time - venue" meta and
 * one window-derived badge. This test checks the adapter maps those three, not the divider (that
 * is [com.thedavelopers.eventqr.ui.components.ListRowBinder]'s job, covered by
 * `ListRowDateBinderTest`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class RegisteredEventAdapterTest {

    private val now: Instant = Instant.now()

    private fun registration(startOffsetHours: Long, endOffsetHours: Long) = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com",
        attendeeName = "A",
        status = RegistrationStatus.REGISTERED,
        eventTitle = "DevFest Manila 2026",
        eventStartAt = now.plus(startOffsetHours, ChronoUnit.HOURS),
        eventEndAt = now.plus(endOffsetHours, ChronoUnit.HOURS),
        eventLocation = "Quezon City",
    )

    private fun bind(item: RegistrationResponse, onClick: (RegistrationResponse) -> Unit = {}): View {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = ContextThemeWrapper(base, R.style.Theme_EventQR)
        val adapter = RegisteredEventAdapter(onClick)
        adapter.submitItems(listOf(item))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    @Test
    fun rowReadsTitleTimeAndVenue() {
        val item = registration(24, 27)

        val row = bind(item)

        assertEquals("DevFest Manila 2026", row.text(R.id.listRowTitle))
        assertEquals(
            "${EventCardPresenter.dateParts(item.eventStartAt).time} - Quezon City",
            row.text(R.id.listRowMeta),
        )
    }

    @Test
    fun aBlankEventTitlePrintsTheUntitledFallback() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = ContextThemeWrapper(base, R.style.Theme_EventQR)

        val row = bind(registration(24, 27).copy(eventTitle = "   "))

        assertEquals(context.getString(R.string.common_untitled_event), row.text(R.id.listRowTitle))
    }

    @Test
    fun badgeFollowsTheEventWindow() {
        assertEquals("Active", bind(registration(-1, 2)).text(R.id.listRowBadge))
        assertEquals("Upcoming", bind(registration(24, 27)).text(R.id.listRowBadge))
        assertEquals("Completed", bind(registration(-48, -45)).text(R.id.listRowBadge))
    }

    @Test
    fun rowClickCarriesTheRegistration() {
        val item = registration(24, 27)
        var opened: RegistrationResponse? = null

        bind(item) { opened = it }.findViewById<View>(R.id.listRowContainer).performClick()

        assertEquals(item, opened)
    }

    // Stable ids drive DiffUtil and row animations: an out-of-range pre-layout position must answer
    // NO_ID, and two rows must never share an id.
    @Test
    fun stableIdsAnswerNoIdOutOfRangeAndDifferPerRow() {
        val adapter = RegisteredEventAdapter {}
        adapter.submitItems(listOf(registration(24, 27), registration(24, 27)))

        assertEquals(RecyclerView.NO_ID, adapter.getItemId(-1))
        assertEquals(RecyclerView.NO_ID, adapter.getItemId(2))
        assertNotEquals(adapter.getItemId(0), adapter.getItemId(1))
    }
}
