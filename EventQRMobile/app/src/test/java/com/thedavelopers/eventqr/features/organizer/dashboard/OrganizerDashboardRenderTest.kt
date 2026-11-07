package com.thedavelopers.eventqr.features.organizer.dashboard

import android.view.View
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
    fun errorHidesTheEmptyState_andALaterEmptySuccessRestoresTheCta() {
        val activity = build()

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutEventsEmpty).visibility)

        activity.renderDashboard(success(emptyList()))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutEventsEmpty).visibility)
        assertEquals("Create event", activity.findViewById<TextView>(R.id.emptyAction).text.toString())
    }

    @Test
    fun failedLoad_doesNotClearTheSavedSelection() {
        val activity = build()
        OrganizerRepository(activity).saveSelectedEventId("saved-event")

        activity.renderDashboard(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, "offline"))

        assertEquals("saved-event", OrganizerRepository(activity).getSelectedEventId())
    }
}
