package com.thedavelopers.eventqr.features.organizer.dashboard

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.lifecycleStatus
import com.thedavelopers.eventqr.features.organizer.testOrganizerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * §6.4 wiring: the pass head carries REGISTERED / ATTENDED / LIVE NOW, the rows carry
 * Upcoming / Ended / Redeemed, and a failed load prints `--` in all six cells, never `0`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class OrganizerDashboardRenderTest {

    private fun build(): OrganizerDashboardActivity =
        Robolectric.buildActivity(OrganizerDashboardActivity::class.java).create().get()

    private fun event(
        startAt: Instant,
        endAt: Instant,
        registered: Long,
        attended: Long,
        redemptions: Long,
    ): OrganizerMvpEvent = testOrganizerEvent().copy(
        eventStartAt = startAt,
        eventEndAt = endAt,
        registeredCount = registered,
        attendedCount = attended,
        rewardRedemptions = redemptions,
    )

    private fun OrganizerDashboardActivity.headCells(): List<Pair<String, String>> {
        val values = listOf(R.id.passHeadStat1Value, R.id.passHeadStat2Value, R.id.passHeadStat3Value)
        val labels = listOf(R.id.passHeadStat1Label, R.id.passHeadStat2Label, R.id.passHeadStat3Label)
        return labels.zip(values) { label, value ->
            findViewById<TextView>(label).text.toString() to findViewById<TextView>(value).text.toString()
        }
    }

    /** Each summaryCard is a vertical row: horizontal label/value line, then the hairline. */
    private fun OrganizerDashboardActivity.metricCells(): List<Pair<String, String>> {
        val rows = findViewById<LinearLayout>(R.id.metricRowsContainer)
        return (0 until rows.childCount).map { index ->
            val line = (rows.getChildAt(index) as LinearLayout).getChildAt(0) as LinearLayout
            (line.getChildAt(0) as TextView).text.toString() to
                (line.getChildAt(1) as TextView).text.toString()
        }
    }

    private fun success(events: List<OrganizerMvpEvent>) =
        OrganizerMvpLoad(events, OrganizerMvpDataSource.BACKEND)

    @Test
    fun eventList_drivesTheHeadAndTheThreeRows() {
        val activity = build()
        val now = Instant.now()
        val events = listOf(
            event(now.minusSeconds(3_600), now.plusSeconds(3_600), 120, 80, 9),
            event(now.plusSeconds(86_400), now.plusSeconds(172_800), 30, 0, 4),
            event(now.minusSeconds(172_800), now.minusSeconds(86_400), 50, 10, 6),
        )

        activity.renderDashboard(success(events))

        assertEquals(
            listOf(
                "Registered" to "200",
                "Attended" to "90",
                "Live now" to "1",
            ),
            activity.headCells(),
        )
        assertEquals(
            listOf(
                "Upcoming" to "1",
                "Ended" to "1",
                "Redeemed" to "19",
            ),
            activity.metricCells(),
        )
    }

    @Test
    fun unapprovedEvents_neverReachAnyNumber() {
        val activity = build()
        val now = Instant.now()
        val included = listOf(
            event(now.minusSeconds(3_600), now.plusSeconds(3_600), 120, 80, 9),
        )
        val excluded = listOf("Draft", "Pending Review", "Cancelled").map { status ->
            event(now.minusSeconds(3_600), now.plusSeconds(3_600), 999, 999, 999).copy(status = status)
        }

        activity.renderDashboard(success(included + excluded))

        assertEquals(
            listOf(
                "Registered" to "120",
                "Attended" to "80",
                "Live now" to "1",
            ),
            activity.headCells(),
        )
        assertEquals(
            listOf(
                "Upcoming" to "0",
                "Ended" to "0",
                "Redeemed" to "9",
            ),
            activity.metricCells(),
        )
    }

    @Test
    fun failedLoad_printsTheUnavailableTokenInHeadAndRows() {
        val activity = build()

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))

        assertEquals(List(3) { "--" }, activity.headCells().map { it.second })
        assertEquals(List(3) { "--" }, activity.metricCells().map { it.second })
    }

    @Test
    fun successfulEmptyLoad_printsRealZeros() {
        val activity = build()

        activity.renderDashboard(success(emptyList()))

        assertEquals(List(3) { "0" }, activity.headCells().map { it.second })
        assertEquals(List(3) { "0" }, activity.metricCells().map { it.second })
    }

    @Test
    fun lifecycleCounts_matchLifecycleStatus() {
        val now = Instant.now()
        val cases = listOf(
            event(now.minusSeconds(3_600), now.plusSeconds(3_600), 0, 0, 0),
            event(now.plusSeconds(3_600), now.plusSeconds(7_200), 0, 0, 0),
            event(now.minusSeconds(7_200), now.minusSeconds(3_600), 0, 0, 0),
            testOrganizerEvent().copy(eventStartAt = null, eventEndAt = null),
        )

        for (case in cases) {
            val status = case.lifecycleStatus()
            val numbers = OrganizerDashboardNumbers.of(listOf(case), now = now)
            assertEquals("liveNow for $status", if (status == "Active") 1L else 0L, numbers.liveNow)
            assertEquals("upcoming for $status", if (status == "Upcoming") 1L else 0L, numbers.upcoming)
            assertEquals("ended for $status", if (status == "Completed") 1L else 0L, numbers.ended)
        }
    }

    @Test
    fun theRequestAction_survivesErrorAndEmpty_andTheEmptyStateCarriesNoSecondControl() {
        val activity = build()
        val requestAction = activity.findViewById<View>(R.id.btnRequestEvent)

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutEventsEmpty).visibility)
        assertEquals(View.VISIBLE, requestAction.visibility)

        activity.renderDashboard(success(emptyList()))
        val emptyState = activity.findViewById<View>(R.id.layoutEventsEmpty)
        assertEquals(View.VISIBLE, emptyState.visibility)
        assertEquals(View.GONE, emptyState.findViewById<View>(R.id.emptyAction).visibility)
        assertEquals(View.VISIBLE, requestAction.visibility)
        assertEquals("Request event", (requestAction as TextView).text.toString())
    }

    @Test
    fun failedLoad_doesNotClearTheSavedSelection() {
        val activity = build()
        OrganizerRepository(activity).saveSelectedEventId("saved-event")

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))

        assertEquals("saved-event", OrganizerRepository(activity).getSelectedEventId())
    }

    // §5.10 item 6: the failed load owns the list area and its Retry sits under the ALL EVENTS
    // header, above the NEXT EVENT / ACTIVE blocks - never below the fold.
    @Test
    fun failedLoad_putsTheErrorAboveTheNextEventAndActiveBlocks() {
        val activity = build()

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))

        val error = activity.findViewById<View>(R.id.layoutDashboardError)
        val column = error.parent as android.view.ViewGroup
        val eyebrow = activity.findViewById<View>(R.id.txtNextEventEyebrow)
        val activeHost = activity.findViewById<View>(R.id.activeEventsContainer).parent as View

        assertEquals(View.VISIBLE, error.visibility)
        assertTrue(
            "the error must come before the NEXT EVENT eyebrow",
            column.indexOfChild(error) < column.indexOfChild(eyebrow),
        )
        assertTrue(
            "the error must come before the ACTIVE block",
            column.indexOfChild(error) < column.indexOfChild(activeHost),
        )
    }

    // Returning from a create/edit page reloads once per resume; the first resume is the onCreate
    // load, so it must not issue a second request.
    @Test
    fun resumeReloadsOncePerReturnAndNeverOnTheFirstResume() {
        val controller = Robolectric.buildActivity(OrganizerDashboardActivity::class.java)
        val activity = controller.create().start().resume().get()
        assertEquals(0, activity.resumeReloads)

        controller.pause().resume()
        assertEquals(1, activity.resumeReloads)

        controller.pause().resume()
        assertEquals(2, activity.resumeReloads)
    }

    // A reload over rendered content refreshes in place: no skeleton over readable numbers.
    @Test
    fun aReloadAfterRenderedContentNeverShowsTheSkeleton() {
        val activity = build()
        activity.renderDashboard(success(emptyList()))

        activity.loadDashboard()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.skeletonLoading).visibility)
    }

    // The metric rows grow with the label at large font scale and keep the 48dp floor.
    @Test
    fun metricRowsWrapContentAndKeepTheTouchFloor() {
        val activity = build()
        activity.renderDashboard(success(emptyList()))
        val rows = activity.findViewById<LinearLayout>(R.id.metricRowsContainer)

        assertEquals(3, rows.childCount)
        val floor = activity.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height)
        for (index in 0 until rows.childCount) {
            val line = (rows.getChildAt(index) as LinearLayout).getChildAt(0) as LinearLayout
            assertEquals(LinearLayout.HORIZONTAL, line.orientation)
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, line.layoutParams.height)
            assertEquals("row $index must keep its touch floor", floor, line.minimumHeight)
        }
    }

    // The eyebrow only exists when its row does; a failure resolves no next event at all.
    @Test
    fun theNextEventEyebrowExistsOnlyWithARow() {
        val activity = build()
        val eyebrow = activity.findViewById<View>(R.id.txtNextEventEyebrow)
        val host = activity.findViewById<View>(R.id.nextEventHost)

        activity.renderDashboard(success(emptyList()))
        assertEquals(View.GONE, eyebrow.visibility)
        assertEquals(View.GONE, host.visibility)

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))
        assertEquals(View.GONE, eyebrow.visibility)

        val now = Instant.now()
        activity.renderDashboard(success(listOf(event(now.plusSeconds(3_600), now.plusSeconds(7_200), 1, 0, 0))))
        assertEquals(View.VISIBLE, eyebrow.visibility)
        assertEquals(View.VISIBLE, host.visibility)
    }

    // NEXT EVENT is the earliest event that has not ended: the API order and the saved selection
    // must never put a completed event in that row.
    @Test
    fun theNextEventIsTheEarliestEventThatHasNotEnded() {
        val activity = build()
        val now = Instant.now()
        val ended = event(now.minusSeconds(7_200), now.minusSeconds(3_600), 0, 0, 0).copy(title = "Ended")
        val later = event(now.plusSeconds(86_400), now.plusSeconds(90_000), 0, 0, 0).copy(title = "Later")
        val sooner = event(now.plusSeconds(3_600), now.plusSeconds(7_200), 0, 0, 0).copy(title = "Sooner")

        // Completed first, earlier start than the others: firstOrNull() used to hand it the row.
        activity.renderDashboard(success(listOf(ended, later, sooner)))

        val host = activity.findViewById<ViewGroup>(R.id.nextEventHost)
        assertEquals(View.VISIBLE, host.visibility)
        assertEquals("Sooner", host.findViewById<TextView>(R.id.listRowTitle).text.toString())
    }

    @Test
    fun aCompletedEventNeverTakesTheNextEventRow() {
        val activity = build()
        val now = Instant.now()

        activity.renderDashboard(
            success(listOf(event(now.minusSeconds(7_200), now.minusSeconds(3_600), 0, 0, 0))),
        )

        assertEquals(View.GONE, activity.findViewById<View>(R.id.nextEventHost).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtNextEventEyebrow).visibility)
    }
}
