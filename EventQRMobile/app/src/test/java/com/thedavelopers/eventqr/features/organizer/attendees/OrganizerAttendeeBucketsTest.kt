package com.thedavelopers.eventqr.features.organizer.attendees

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpAttendee
import com.thedavelopers.eventqr.features.organizer.checkedInTotal
import com.thedavelopers.eventqr.features.organizer.matchesOrganizerAttendeeQuery
import com.thedavelopers.eventqr.features.organizer.registeredTotal
import com.thedavelopers.eventqr.features.organizer.statusBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class OrganizerAttendeeBucketsTest {
    private fun attendee(status: String, counted: Boolean = true, registration: String = "REGISTERED") = OrganizerMvpAttendee(
        id = status + counted,
        eventId = "e",
        name = "Ana",
        email = "ana@x.com",
        phone = "-",
        registrationStatus = registration,
        currentEventStatus = status,
        points = 0,
        lastTransactionTime = "-",
        registeredDate = "-",
        qrCredentialStatus = "Issued",
        recentTransactions = emptyList(),
        recentRejectedScans = emptyList(),
        countedAsRegistered = counted,
    )

    @Test
    fun buckets_comeFromCurrentEventStatus() {
        assertEquals("Registered", attendee("Registered").statusBucket())
        assertEquals("Checked In", attendee("Checked In").statusBucket())
        assertEquals("Exited", attendee("Exited").statusBucket())
        assertEquals("Cancelled", attendee("Cancelled", counted = false).statusBucket())
        assertEquals("No Show", attendee("No Show", counted = false).statusBucket())

        // Raw enum values
        assertEquals("Checked In", attendee("ENTERED").statusBucket())
        assertEquals("Exited", attendee("EXITED").statusBucket())
        assertEquals("Cancelled", attendee("CANCELLED", counted = false).statusBucket())
        assertEquals("No Show", attendee("NO_SHOW", counted = false).statusBucket())
        assertEquals("Registered", attendee("REGISTERED").statusBucket())

        // Fallback to registrationStatus when currentEventStatus is blank
        assertEquals("Checked In", attendee("", registration = "ENTERED").statusBucket())
        assertEquals("Exited", attendee("", registration = "EXITED").statusBucket())
        assertEquals("Cancelled", attendee("", counted = false, registration = "CANCELLED").statusBucket())
        assertEquals("No Show", attendee("", counted = false, registration = "NO_SHOW").statusBucket())
        assertEquals("Registered", attendee("", registration = "REGISTERED").statusBucket())
    }

    @Test
    fun checkedIn_doesNotIncludeExited() {
        val list = listOf(attendee("Checked In"), attendee("Exited"), attendee("Registered"))
        assertEquals(1, list.checkedInTotal())
        assertTrue(attendee("Exited").matchesOrganizerAttendeeQuery("", "Exited"))
        assertFalse(attendee("Exited").matchesOrganizerAttendeeQuery("", "Checked In"))
    }

    @Test
    fun total_countsOnlyCountedAsRegistered() {
        val list = listOf(
            attendee("Registered"),
            attendee("Checked In"),
            attendee("Cancelled", counted = false),
            attendee("No Show", counted = false),
        )
        assertEquals(2, list.registeredTotal())
    }

    private fun texts(view: View, out: MutableList<String> = mutableListOf()): List<String> {
        if (view is TextView) out.add(view.text.toString())
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), out)
        return out
    }

    @Test
    fun attendeeManagementScreen_hasNoNoShowTile() {
        val activity = Robolectric.buildActivity(AttendeeManagementActivity::class.java).create().get()
        val all = texts(activity.window.decorView)
        assertFalse("No Show tile must be hidden: $all", all.any { it.equals("No Show", ignoreCase = true) })
        assertTrue(all.any { it == "Total" })
        assertTrue(all.any { it == "Checked In" })
    }

    @Test
    fun searchAttendeesScreen_hasNoNoShowChip() {
        val activity = Robolectric.buildActivity(
            SearchAttendeesActivity::class.java,
            android.content.Intent().putExtra(com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID, "event-1"),
        ).create().get()
        val all = texts(activity.window.decorView)
        assertFalse("No Show chip must be hidden: $all", all.any { it.equals("No Show", ignoreCase = true) })
        assertTrue(all.any { it == "Exited" })
    }
}
