package com.thedavelopers.eventqr.features.staff.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.EventSpinnerOption
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The scan picker lists assigned events that are ACTIVE right now (§5.13), never the whole
 * assignment list: an upcoming or ended event is a target that cannot be scanned, and the
 * Assigned events screen's `Active` badge is the only rule that decides it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ScannerActiveEventsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val activeA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val activeB = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val upcoming = UUID.fromString("00000000-0000-0000-0000-00000000000c")
    private val completed = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    private val unassigned = UUID.fromString("00000000-0000-0000-0000-00000000000e")
    private val endedByStatus = UUID.fromString("00000000-0000-0000-0000-00000000000f")
    private val cancelled = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val draft = UUID.fromString("00000000-0000-0000-0000-000000000011")

    // A window wide enough that "running now" never depends on the machine's clock.
    private val runningStart = Instant.parse("2020-01-01T00:00:00Z")
    private val runningEnd = Instant.parse("2100-01-01T00:00:00Z")

    private fun event(
        eventId: UUID,
        title: String,
        status: EventStatus = EventStatus.ACTIVE,
        startAt: Instant? = runningStart,
        endAt: Instant? = runningEnd,
        canScan: Boolean = true,
    ) = StaffAssignedEventResponse(
        assignmentId = UUID.randomUUID(),
        eventId = eventId,
        title = title,
        eventStartAt = startAt,
        eventEndAt = endAt,
        status = status,
        canScan = canScan,
    )

    private fun runningA() = event(activeA, "Tech Summit 2026")
    private fun runningB() = event(activeB, "DevFest Manila 2026")
    private fun upcomingEvent() = event(
        upcoming,
        "Mindanao Startup Pitch Night",
        // The backend stamps ACTIVE before the start time; only the dates make it upcoming.
        status = EventStatus.ACTIVE,
        startAt = Instant.parse("2099-01-01T00:00:00Z"),
        endAt = Instant.parse("2099-01-02T00:00:00Z"),
    )
    private fun completedEvent() = event(
        completed,
        "Laguna Robotics Challenge",
        status = EventStatus.ACTIVE,
        startAt = Instant.parse("2020-01-01T00:00:00Z"),
        endAt = Instant.parse("2020-01-02T00:00:00Z"),
    )
    private fun unassignedEvent() = event(unassigned, "Zamboanga Summer Arts Fest", canScan = false)

    /**
     * The backend still says ENDED while the dates say the window is open. Dates decide (finding:
     * `canStartScan` hid exactly this row while the Assigned badge called it Active), so it is listed.
     */
    private fun backendEndedButRunningEvent() = event(endedByStatus, "Ended flag, running now", status = EventStatus.ENDED)
    private fun cancelledEvent() = event(cancelled, "Cancelled summit", status = EventStatus.CANCELLED)
    private fun draftEvent() = event(draft, "Draft assembly", status = EventStatus.DRAFT)

    private fun entryPurpose(eventId: UUID) = ScanPurposeResponse(
        scanPurposeId = UUID.randomUUID(),
        eventId = eventId,
        name = "Entry",
        code = ScanPurposeCode.ENTRY,
        active = true,
        trackingOnly = false,
    )

    @Before
    fun seedStaffSession() {
        SessionManager(context).apply {
            clearSession()
            saveRole(AccountRole.STAFF)
        }
        InjectedScannerActivity.repository = null
    }

    @Test
    fun onlyAssignedActiveEventsReachThePicker_inTheirServerOrder() {
        val view = RecordingView()
        val repository = object : StaffRepository(context) {
            override suspend fun getEvents() = NetworkResult.Success(
                listOf(
                    runningA(),
                    upcomingEvent(),
                    completedEvent(),
                    unassignedEvent(),
                    backendEndedButRunningEvent(),
                    cancelledEvent(),
                    draftEvent(),
                    runningB(),
                ),
            )
        }
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val presenter = ScannerPresenter(view, repository, UiStrings(context), CoroutineScope(dispatcher))
        try {
            presenter.loadEvents()
            awaitUntil { view.events.isNotEmpty() }
            // Draft and Cancelled keep their backend status (the badge prints them, so the picker does
            // too); Unassigned (`canScan = false`) and the date-derived Upcoming/Completed rows are out
            // for the same reason - none of them can be scanned now. The backend-ENDED row with a
            // running window stays: dates decide for the time-derived statuses.
            assertEquals(
                listOf(activeA.toString(), endedByStatus.toString(), activeB.toString()),
                view.events.single().map { it.id },
            )
            // The ended notice is gone with the filter: the empty state now says why the list is empty.
            assertTrue(view.messages.isEmpty())
        } finally {
            presenter.detach()
            dispatcher.close()
        }
    }

    @Test
    fun rememberedEventThatIsNoLongerActive_fallsBackToTheFirstActiveEvent() {
        val fake = FakeRepository(listOf(upcomingEvent(), runningA(), runningB()))
        InjectedScannerActivity.repository = fake
        val controller = scannerController(upcoming)
        val activity = controller.get()

        awaitMain { activity.selectedEvent() != null }

        assertEquals(activeA.toString(), activity.selectedEvent()?.id)

        // The saved id is the resolved one, so recreating the screen cannot resurrect the stale event.
        val state = Bundle()
        controller.saveInstanceState(state)
        val restored = Robolectric.buildActivity(InjectedScannerActivity::class.java, scannerIntent(upcoming))
            .create(state)
            .get()

        awaitMain { restored.selectedEvent() != null }

        assertEquals(activeA.toString(), restored.selectedEvent()?.id)
    }

    @Test
    fun resumeRefresh_bringsInAnEventThatHasJustStarted() {
        val fake = FakeRepository(listOf(upcomingEvent()))
        InjectedScannerActivity.repository = fake
        val controller = scannerController(upcoming)
        val activity = controller.get()

        awaitMain { activity.findViewById<View>(R.id.txtScannerEmptyState).visibility == View.VISIBLE }
        assertNull(activity.selectedEvent())

        // The same event, now running: the list is clock-derived, so coming back to the screen re-reads it.
        fake.events = listOf(event(upcoming, "Mindanao Startup Pitch Night"))
        controller.pause().resume()

        awaitMain { activity.selectedEvent() != null }

        assertEquals(upcoming.toString(), activity.selectedEvent()?.id)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtScannerEmptyState).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanPanel).visibility)
    }

    @Test
    fun resumeRefresh_dropsAnEventThatHasEnded_andRefusesToSubmit() {
        val fake = FakeRepository(listOf(runningA()), purposes = listOf(entryPurpose(activeA)))
        InjectedScannerActivity.repository = fake
        val controller = scannerController(activeA)
        val activity = controller.get()

        awaitMain { activity.selectedPurpose() != null }

        // The event ends while the screen is paused: its dates now sit in the past.
        fake.events = listOf(
            event(
                activeA,
                "Tech Summit 2026",
                startAt = Instant.parse("2020-01-01T00:00:00Z"),
                endAt = Instant.parse("2020-01-02T00:00:00Z"),
            ),
        )
        controller.pause().resume()

        awaitMain { activity.selectedEvent() == null }

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.txtScannerEmptyState).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanPanel).visibility)
        assertNull(activity.selectedPurpose())
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        assertEquals(0, fake.verifyCalls)
        assertTrue(fake.recordRequests.isEmpty())
    }

    @Test
    fun singleActiveEvent_isAutoSelected_andItsPurposesStillLoad() {
        val fake = FakeRepository(listOf(runningA()), purposes = listOf(entryPurpose(activeA)))
        InjectedScannerActivity.repository = fake
        val activity = scanner(activeA)

        awaitMain { activity.selectedPurpose() != null }

        assertEquals(activeA.toString(), activity.selectedEvent()?.id)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanPanel).visibility)
        assertEquals(1, fake.purposeCalls)
    }

    @Test
    fun zeroActiveEvents_showsTheEmptyState_andRefusesToScan() {
        val fake = FakeRepository(listOf(upcomingEvent(), completedEvent(), unassignedEvent()))
        InjectedScannerActivity.repository = fake
        val activity = scanner(activeA)

        awaitMain { activity.findViewById<View>(R.id.txtScannerEmptyState).visibility == View.VISIBLE }

        assertNull(activity.selectedEvent())
        val empty = activity.findViewById<EventQrEmptyState>(R.id.txtScannerEmptyState)
        assertEquals(activity.getString(R.string.ui_staff_no_active_events), empty.title.toString())
        assertEquals(activity.getString(R.string.ui_staff_no_active_events_body), empty.subtext.toString())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanPanel).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupManualEntry).visibility)
        assertEquals(
            activity.getString(R.string.ui_staff_no_active_events),
            activity.findViewById<android.widget.TextView>(R.id.scanContextEvent).text.toString(),
        )

        // Nothing scannable: a submit leaves the guard, never the network.
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        assertEquals(0, fake.verifyCalls)
        assertTrue(fake.recordRequests.isEmpty())
    }

    /** Inside the injected repository, so the screen never touches the real API. */
    class InjectedScannerActivity : ScannerActivity() {
        override fun newRepository(): StaffRepository = repository ?: super.newRepository()

        companion object {
            var repository: StaffRepository? = null
        }
    }

    private fun scannerIntent(eventId: UUID) =
        Intent(context, InjectedScannerActivity::class.java)
            .putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId.toString())

    private fun scannerController(eventId: UUID): ActivityController<InjectedScannerActivity> =
        Robolectric.buildActivity(InjectedScannerActivity::class.java, scannerIntent(eventId))
            .create()
            .start()
            .resume()

    private fun scanner(eventId: UUID): ScannerActivity = scannerController(eventId).get()

    private inner class FakeRepository(
        var events: List<StaffAssignedEventResponse>,
        private val purposes: List<ScanPurposeResponse> = emptyList(),
    ) : StaffRepository(context) {
        var purposeCalls = 0
        var verifyCalls = 0
        val recordRequests = mutableListOf<TransactionRequest>()

        override suspend fun getEvents() = NetworkResult.Success(events)

        override suspend fun getScanPurposesByEvent(eventId: String): NetworkResult<List<ScanPurposeResponse>> {
            purposeCalls++
            return NetworkResult.Success(purposes)
        }

        override suspend fun verifyScan(request: TransactionRequest): NetworkResult<ScanVerificationResponse> {
            verifyCalls++
            return NetworkResult.Error("unexpected verification")
        }

        override suspend fun createTransaction(
            request: TransactionRequest,
            purposeCode: ScanPurposeCode,
        ): NetworkResult<TransactionResponse> {
            recordRequests += request
            return NetworkResult.Error("unexpected scan")
        }
    }

    private class RecordingView : ScannerContract.View {
        val events = Collections.synchronizedList(mutableListOf<List<EventSpinnerOption>>())
        val messages = Collections.synchronizedList(mutableListOf<String>())

        override fun showEvents(items: List<EventSpinnerOption>) {
            events += items
        }

        override fun showEventsLoadFailed(message: String) {
            messages += message
        }

        override fun showPurposes(items: List<ScanPurposeResponse>) = Unit
        override fun showPurposesUnavailable(message: String) = Unit
        override fun showRecordedResult(result: TransactionResponse) = Unit
        override fun showVerifiedResult(result: ScanVerificationResponse) = Unit
        override fun showRecordFailed(message: String) = Unit
        override fun showRewardRedemptionScanResult(result: RewardRedemptionScanResponse) = Unit
        override fun showScanError(message: String) = Unit

        override fun showMessage(message: String) {
            messages += message
        }

        override fun showLoading(isLoading: Boolean) = Unit
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
