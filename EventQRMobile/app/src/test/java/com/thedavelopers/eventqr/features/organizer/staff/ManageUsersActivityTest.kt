package com.thedavelopers.eventqr.features.organizer.staff

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.organizer.testOrganizerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * §6.4/§6.7: Add staff is the pinned primary footer, and it is the only Add staff control. The
 * empty state carries no action of its own, and a failed load with zero rows leaves Retry as the
 * only control.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ManageUsersActivityTest {

    /**
     * No event id in the intent, so onCreate stops at the missing-event state and never reaches the
     * repository; [ManageUsersActivity.buildScreen] then renders the real layout offline.
     */
    private fun screen(): ManageUsersActivity {
        val activity = Robolectric.buildActivity(ManageUsersActivity::class.java).create().get()
        activity.selectedEvent = testOrganizerEvent()
        activity.buildScreen()
        return activity
    }

    private fun ManageUsersActivity.footer(): View = findViewById(R.id.staffAddFooter)

    private fun ManageUsersActivity.staff() = OrganizerMvpStaff(
        id = "assignment-1",
        name = "Ana Reyes",
        email = "ana@example.com",
        assignedEventId = selectedEvent.id,
        assignedEvent = selectedEvent.title,
        roleLabel = "Staff",
        accessStatus = "Active",
        addedDate = "-",
        permissions = listOf("Scan QR"),
    )

    private fun load(
        staff: List<OrganizerMvpStaff>,
        source: OrganizerMvpDataSource = OrganizerMvpDataSource.BACKEND,
        message: String? = null,
    ) = OrganizerMvpLoad(staff, source, message)

    /** Counts reload requests so the strip's Retry is testable without a network call. */
    private class CountingManageUsersActivity : ManageUsersActivity() {
        var reloads = 0

        override fun loadAssigned() {
            reloads++
        }
    }

    @Test
    fun errorStripRetry_clearsTheStripAndRequestsAReload() {
        val activity = Robolectric.buildActivity(CountingManageUsersActivity::class.java).create().get()
        activity.selectedEvent = testOrganizerEvent()
        activity.buildScreen()
        activity.applyStaffLoad(load(listOf(activity.staff())))
        activity.applyStaffLoad(load(emptyList(), OrganizerMvpDataSource.ERROR, "Offline"))
        val strip = activity.findViewById<View>(R.id.errorStaffAssignment)
        assertEquals(View.VISIBLE, strip.visibility)

        activity.findViewById<View>(R.id.btnErrorStripRetry).performClick()

        assertEquals(View.GONE, strip.visibility)
        assertEquals("Retry must re-run the load", 1, activity.reloads)
    }

    @Test
    fun footerButton_opensAddStaffForThisEvent() {
        val activity = screen()
        activity.applyStaffLoad(load(emptyList()))

        activity.findViewById<View>(R.id.btnAddStaff).performClick()

        val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull("the footer did not open Add staff", intent)
        assertEquals(SearchUserAccountActivity::class.java.name, intent.component?.className)
        assertEquals(activity.selectedEvent.id, intent.getStringExtra(EXTRA_EVENT_ID))
    }

    @Test
    fun footerIsGoneBeforeTheFirstLoad() {
        assertEquals(View.GONE, screen().footer().visibility)
    }

    @Test
    fun loadedWithRows_showsTheFooter() {
        val activity = screen()

        activity.applyStaffLoad(load(listOf(activity.staff())))

        assertEquals(View.VISIBLE, activity.footer().visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recyclerStaffAssignment).visibility)
    }

    @Test
    fun loadedWithZeroRows_showsTheFooterAsTheOnlyAddStaffControl() {
        val activity = screen()

        activity.applyStaffLoad(load(emptyList()))

        assertEquals(View.VISIBLE, activity.footer().visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.txtEmptyStaff).visibility)
        assertEquals(
            "the empty state must not repeat the footer's Add staff action",
            1,
            activity.window.decorView.allTexts().count { it == activity.getString(R.string.ui_organizer_add_staff) },
        )
    }

    @Test
    fun failedLoadWithZeroRows_hidesTheFooterAndKeepsRetry() {
        val activity = screen()

        activity.applyStaffLoad(load(emptyList(), OrganizerMvpDataSource.ERROR, "Offline"))

        assertEquals(View.GONE, activity.footer().visibility)
        val texts = activity.window.decorView.allTexts()
        assertTrue("the failure message is missing", texts.contains("Offline"))
        assertTrue(
            "Retry must stay the only action on a failed load",
            texts.contains(activity.getString(R.string.common_retry)),
        )
        assertFalse(texts.contains(activity.getString(R.string.ui_organizer_add_staff)))
    }

    @Test
    fun failedRefreshWithRows_keepsTheRowsTheFooterAndTheInFlowStrip() {
        val activity = screen()
        activity.applyStaffLoad(load(listOf(activity.staff())))

        activity.applyStaffLoad(load(emptyList(), OrganizerMvpDataSource.ERROR, "Offline"))

        // §5.16: readable rows survive a failed refresh, so the footer stays and the strip reports.
        assertEquals(View.VISIBLE, activity.footer().visibility)
        val recycler = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerStaffAssignment)
        assertEquals(View.VISIBLE, recycler.visibility)
        assertEquals("the readable row was wiped", 1, recycler.adapter?.itemCount)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.errorStaffAssignment).visibility)
        assertTrue(
            "the refresh failure is missing",
            activity.window.decorView.allTexts().contains("Offline"),
        )
    }
}
