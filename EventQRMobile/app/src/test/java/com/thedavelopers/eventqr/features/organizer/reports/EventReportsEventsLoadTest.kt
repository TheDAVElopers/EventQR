package com.thedavelopers.eventqr.features.organizer.reports

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.organizer.testOrganizerEvent
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.organizer.findButton
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** N4: a failed organizer-events fetch shows an error with Retry, never "No events found". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventReportsEventsLoadTest {

    private fun build(): EventReportsActivity =
        Robolectric.buildActivity(EventReportsActivity::class.java).create().get()

    private fun failed(message: String?) =
        OrganizerMvpLoad(emptyList<OrganizerMvpEvent>(), OrganizerMvpDataSource.ERROR, message)

    private fun EventReportsActivity.texts() = window.decorView.allTexts()

    @Test
    fun failedLoad_showsTheErrorWithRetry_notTheEmptyState() {
        val activity = build()

        activity.onEventsLoaded(failed("Network problem. Check your connection and try again."))

        val texts = activity.texts()
        assertTrue(texts.contains("Network problem. Check your connection and try again."))
        assertFalse(texts.contains(activity.getString(R.string.event_reports_no_events_title)))
        assertNotNull(activity.window.decorView.findButton(activity.getString(R.string.organizer_retry)))
    }

    @Test
    fun failedLoadWithoutMessage_usesTheGenericEventsLoadError() {
        val activity = build()

        activity.onEventsLoaded(failed(null))

        assertTrue(activity.texts().contains(activity.getString(R.string.organizer_dashboard_events_load_failed)))
    }

    @Test
    fun retry_replacesTheErrorWithTheLoadingSkeleton() {
        val activity = build()
        activity.onEventsLoaded(failed("Timeout"))

        activity.window.decorView.findButton(activity.getString(R.string.organizer_retry))!!.performClick()

        assertFalse(activity.texts().contains("Timeout"))
        assertNull(activity.window.decorView.findButton(activity.getString(R.string.organizer_retry)))
    }

    private class FakeReportsRepository :
        OrganizerReportsRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun fetchSummary(eventId: String): NetworkResult<EventReportSummaryDto> =
            NetworkResult.Success(EventReportSummaryDto())
    }

    @Test
    fun eventSelector_usesTheAlreadyLoadedEventList() {
        val activity = build()
        activity.reportsRepository = FakeReportsRepository()
        val events = listOf(testOrganizerEvent("Night Market"), testOrganizerEvent("Day Fair"))

        // No second fetch: in this test a fetch would fail and leave the selector without the event name.
        activity.onEventsLoaded(OrganizerMvpLoad(events, OrganizerMvpDataSource.BACKEND))

        assertTrue(activity.texts().contains("Night Market"))
        assertNull(activity.window.decorView.findButton(activity.getString(R.string.organizer_retry)))
    }

    @Test
    fun successfulEmptyLoad_stillShowsNoEventsFound() {
        val activity = build()

        activity.onEventsLoaded(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.BACKEND))

        assertTrue(activity.texts().contains(activity.getString(R.string.event_reports_no_events_title)))
        assertNull(activity.window.decorView.findButton(activity.getString(R.string.organizer_retry)))
    }
}
