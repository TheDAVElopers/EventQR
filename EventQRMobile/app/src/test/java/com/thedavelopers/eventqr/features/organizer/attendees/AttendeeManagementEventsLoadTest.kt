package com.thedavelopers.eventqr.features.organizer.attendees

import android.view.View
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** N4: a failed organizer-events fetch says so (with pull-to-retry), never "No Events Available". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeManagementEventsLoadTest {

    @Test
    fun failedEventsLoad_showsTheLoadErrorAndKeepsPullToRefreshForRetry() {
        val activity = Robolectric.buildActivity(AttendeeManagementActivity::class.java).create().get()

        activity.showEventsLoadFailedState("Timeout.")

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutAttendeesEmpty).visibility)
        assertEquals(
            activity.getString(R.string.organizer_dashboard_events_load_failed),
            activity.findViewById<TextView>(R.id.txtAttendeesEmptyTitle).text.toString(),
        )
        assertEquals(
            activity.getString(R.string.organizer_events_load_failed_pull_to_retry, "Timeout."),
            activity.findViewById<TextView>(R.id.txtAttendeesEmptySub).text.toString(),
        )
        assertTrue(activity.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshAttendeeManagement).isEnabled)
    }
}
