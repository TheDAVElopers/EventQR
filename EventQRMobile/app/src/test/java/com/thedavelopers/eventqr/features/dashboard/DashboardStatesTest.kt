package com.thedavelopers.eventqr.features.dashboard

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The dashboard body has three exclusive states: first-load skeleton, honest first-load error with
 * one Retry, and rendered rows. A failed refresh over rendered rows reports in the in-flow strip
 * (§5.16), never a toast or a state swap. Resume issues exactly one request.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class DashboardStatesTest {

    private fun summary(
        upcoming: List<DashboardUpcomingEvent> = emptyList(),
        discover: List<DashboardUpcomingEvent> = emptyList(),
    ) = DashboardSummary(
        totalEvents = 0,
        totalRegistrations = 0,
        totalTransactions = 0,
        totalPoints = 0,
        totalNotifications = 0,
        upcomingEvents = upcoming,
        discoverEvents = discover,
    )

    private fun event(title: String = "DevFest Manila") = DashboardUpcomingEvent(
        eventId = UUID.randomUUID(),
        title = title,
        eventStartAt = Instant.now().plus(24, ChronoUnit.HOURS),
        status = "Upcoming",
        isRegistered = true,
    )

    private fun build() = Robolectric.buildActivity(DashboardActivity::class.java).create().get()

    private fun DashboardActivity.upcoming(): LinearLayout = findViewById(R.id.layoutUpcomingEvents)

    private fun DashboardActivity.discover(): LinearLayout = findViewById(R.id.layoutDiscoverEvents)

    private fun DashboardActivity.skeleton(): View = findViewById(R.id.skeletonLoading)

    private fun DashboardActivity.bodyBlock(container: LinearLayout): LinearLayout =
        container.getChildAt(1) as LinearLayout

    @Test
    fun loading_keepsTheSkeletonAndHidesBothBlocks() {
        val activity = build()
        activity.showLoading(true)

        assertEquals(View.VISIBLE, activity.skeleton().visibility)
        assertEquals(View.GONE, activity.upcoming().visibility)
        assertEquals(View.GONE, activity.discover().visibility)

        activity.showLoading(false)

        assertEquals(View.GONE, activity.skeleton().visibility)
        assertEquals(View.VISIBLE, activity.upcoming().visibility)
        assertEquals(View.VISIBLE, activity.discover().visibility)
    }

    @Test
    fun successfulEmptyLoad_rendersTheEmptyRowsNotAnError() {
        val activity = build()

        activity.showSummary(summary())

        assertEquals(View.GONE, activity.skeleton().visibility)
        val next = activity.bodyBlock(activity.upcoming())
        val discover = activity.bodyBlock(activity.discover())
        assertEquals("No upcoming events", (next.getChildAt(0) as TextView).text.toString())
        assertEquals("No events to discover yet", (discover.getChildAt(0) as TextView).text.toString())
        assertEquals(
            "New events open for registration will appear here.",
            (discover.getChildAt(1) as TextView).text.toString(),
        )
        assertTrue(activity.upcoming().getChildAt(1) !is EventQrEmptyState)
    }

    @Test
    fun firstLoadFailure_ownsTheBodyWithOneRetryAndHidesDiscover() {
        val activity = build()

        activity.showError("Connection failed.")

        assertEquals(View.GONE, activity.skeleton().visibility)
        assertEquals(View.VISIBLE, activity.upcoming().visibility)
        assertEquals(View.GONE, activity.discover().visibility)
        assertEquals(2, activity.upcoming().childCount)

        val error = activity.bodyBlock(activity.upcoming()) as EventQrEmptyState
        assertEquals(
            activity.getString(R.string.ui_attendee_dashboard_error_title),
            error.title.toString(),
        )
        val retry = error.findViewById<View>(R.id.emptyAction)
        assertEquals(View.VISIBLE, retry.visibility)
        assertEquals(activity.getString(R.string.common_retry), (retry as TextView).text.toString())
    }

    @Test
    fun firstLoadFailure_neverAddsThePartialErrorStrip() {
        val activity = build()

        activity.showError("Connection failed.")
        // The presenter reports every failing source after a total failure; the empty state already
        // owns the failure, so none of them may add the strip and a second Retry.
        activity.showPartialError("account", "offline")
        activity.showPartialError("events", "offline")
        activity.showPartialError("registrations", "offline")

        assertEquals(View.GONE, activity.findViewById<View>(R.id.errorDashboard).visibility)
        assertEquals(2, activity.upcoming().childCount)
        assertTrue(activity.bodyBlock(activity.upcoming()) is EventQrEmptyState)
    }

    @Test
    fun partialFailureWithRowsOnScreen_reportsInTheStrip() {
        val activity = build()
        activity.showSummary(summary(upcoming = listOf(event())))

        activity.showPartialError("events", "offline")

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.errorDashboard).visibility)
        assertTrue(activity.upcoming().getChildAt(1) !is EventQrEmptyState)
    }

    @Test
    fun failedRefresh_keepsTheRenderedRowsAndReportsInTheStrip() {
        val activity = build()
        activity.showSummary(summary(upcoming = listOf(event("DevFest Manila")), discover = listOf(event())))

        val upcomingChildren = activity.upcoming().childCount
        val title = activity.upcoming().getChildAt(1).findViewById<TextView>(R.id.listRowTitle).text.toString()

        activity.showError("Connection failed.")

        assertEquals(upcomingChildren, activity.upcoming().childCount)
        assertEquals(
            title,
            activity.upcoming().getChildAt(1).findViewById<TextView>(R.id.listRowTitle).text.toString(),
        )
        assertEquals(View.VISIBLE, activity.discover().visibility)
        assertEquals(View.GONE, activity.skeleton().visibility)
        assertTrue(activity.upcoming().getChildAt(1) !is EventQrEmptyState)
        // §5.16: the failure reports in the flow, never a toast.
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.errorDashboard).visibility)
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    // One request per resume: the first resume is the onCreate load, the next resume is the second
    // request - never two summaries racing on the same resume.
    @Test
    fun eachResume_issuesExactlyOneLoad() {
        val controller = Robolectric.buildActivity(DashboardActivity::class.java)
        val activity = controller.create().start().resume().get()
        assertEquals(1, activity.loadRequests)

        controller.pause().resume()
        assertEquals(2, activity.loadRequests)

        controller.pause().resume()
        assertEquals(3, activity.loadRequests)
    }
}
