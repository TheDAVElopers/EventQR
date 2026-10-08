package com.thedavelopers.eventqr.features.staff

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.model.dto.StaffTodaySummary
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class StaffPresentersTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun reg(name: String, status: RegistrationStatus = RegistrationStatus.REGISTERED) = RegistrationResponse(
        registrationId = UUID.randomUUID(), eventId = UUID.randomUUID(), attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "$name@test.com", attendeeName = name, status = status,
        qrCredentialId = UUID.randomUUID(),
    )

    private fun tx(attendee: UUID, type: TransactionType, result: TransactionResult) = TransactionResponse(
        transactionId = UUID.randomUUID(), eventId = UUID.randomUUID(), attendeeUserId = attendee,
        registrationId = UUID.randomUUID(), qrCredentialId = UUID.randomUUID(), scanPurposeId = UUID.randomUUID(),
        transactionType = type, transactionResult = result, pointsDelta = 0,
    )

    /** Runs presenter work eagerly on the calling thread; the fakes never suspend. */
    private fun scope() = CoroutineScope(Dispatchers.Unconfined)

    private fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(10)
    }

    // ------------------------------------------------------------------ registrations

    private class RecordingRegistrationsView : EventRegistrationsContract.View {
        val rendered = mutableListOf<List<RegistrationResponse>>()
        val hasQuery = mutableListOf<Boolean>()
        var counts = RegistrationCounts()
        var selected: List<RegistrationResponse> = emptyList()
        val errors = mutableListOf<String>()
        val messages = mutableListOf<String>()
        override fun renderRegistrations(items: List<RegistrationResponse>, hasQuery: Boolean) {
            rendered += items; this.hasQuery += hasQuery
        }
        override fun renderCounts(counts: RegistrationCounts) { this.counts = counts }
        override fun selectAllForPrint(items: List<RegistrationResponse>) { selected = items }
        override fun showLoadMoreError(message: String) { errors += message }
        override fun showMessage(message: String) { messages += message }
        override fun showLoading(isLoading: Boolean) = Unit
    }

    /** 45 registrations on the server, served in server pages; records every query/page requested. */
    private inner class FakeRegistrations(val all: List<RegistrationResponse>) : StaffRepository(context) {
        val pageCalls = java.util.Collections.synchronizedList(mutableListOf<Triple<String?, Int, Int>>())
        var failAllPages = false
        override suspend fun getRegistrationsPage(eventId: String, query: String?, page: Int, size: Int): NetworkResult<PageResponse<RegistrationResponse>> {
            pageCalls += Triple(query?.trim()?.ifEmpty { null }, page, size)
            val matches = all.filter { query.isNullOrBlank() || it.attendeeName.contains(query, ignoreCase = true) }
            val slice = matches.drop(page * size).take(size)
            return NetworkResult.Success(
                PageResponse(
                    content = slice, totalElements = matches.size.toLong(), number = page, size = size,
                    last = (page + 1) * size >= matches.size, empty = slice.isEmpty(),
                )
            )
        }
        val countCalls = java.util.Collections.synchronizedList(mutableListOf<RegistrationStatus?>())
        var failStatus: RegistrationStatus? = null
        override suspend fun countRegistrations(eventId: String, status: RegistrationStatus?): NetworkResult<Long> {
            countCalls += status
            if (status != null && status == failStatus) return NetworkResult.Error("nope")
            return NetworkResult.Success(all.count { status == null || it.status == status }.toLong())
        }
        override suspend fun getAllRegistrations(eventId: String, query: String?): NetworkResult<List<RegistrationResponse>> =
            if (failAllPages) NetworkResult.Error("capped")
            else NetworkResult.Success(all.filter { query.isNullOrBlank() || it.attendeeName.contains(query, ignoreCase = true) })
    }

    private fun serverRegistrations() =
        (1..45).map { reg("Person$it", if (it <= 10) RegistrationStatus.ENTERED else RegistrationStatus.REGISTERED) }

    @Test
    fun registrations_totalIsServerTotalAndTilesCountEveryPage() {
        val view = RecordingRegistrationsView()
        val repo = FakeRegistrations(serverRegistrations())
        val presenter = EventRegistrationsPresenter(view, repo, UiStrings(context), scope())
        presenter.load("e1")
        

        assertEquals(45L, view.counts.total)
        assertEquals(10L, view.counts.checkedIn)
        assertEquals(35L, view.counts.registered)
        assertEquals(20, view.rendered.last().size)
    }

    @Test
    fun registrations_loadMoreAppendsPagesAndStopsAtLast() {
        val view = RecordingRegistrationsView()
        val repo = FakeRegistrations(serverRegistrations())
        val presenter = EventRegistrationsPresenter(view, repo, UiStrings(context), scope())
        presenter.load("e1")
        
        presenter.loadMore(); 
        presenter.loadMore(); 
        assertEquals(45, view.rendered.last().size)
        assertEquals(45, view.rendered.last().map { it.registrationId }.distinct().size)

        val callsBefore = repo.pageCalls.size
        presenter.loadMore(); 
        assertEquals(callsBefore, repo.pageCalls.size)
    }

    @Test
    fun registrations_searchUsesServerQueryDebouncedAndRestartsAtPageZero() {
        val view = RecordingRegistrationsView()
        val repo = FakeRegistrations(serverRegistrations())
        val presenter = EventRegistrationsPresenter(view, repo, UiStrings(context), scope(), searchDebounceMs = 150)
        presenter.load("e1")
        
        presenter.loadMore(); 
        repo.pageCalls.clear()

        presenter.onQueryChanged("Person4")
        presenter.onQueryChanged("Person44")
        assertTrue("debounced: no request before the delay", repo.pageCalls.isEmpty())
        waitUntil { repo.pageCalls.isNotEmpty() }
        Thread.sleep(300)

        assertEquals(listOf(Triple<String?, Int, Int>("Person44", 0, 20)), repo.pageCalls)
        assertEquals(listOf("Person44"), view.rendered.last().map { it.attendeeName })
        assertEquals(true, view.hasQuery.last())
        // Tiles describe the whole event, not the search result.
        assertEquals(45L, view.counts.total)
    }

    @Test
    fun registrations_selectAllReachesAttendeesBeyondTheFirstPage() {
        val view = RecordingRegistrationsView()
        val repo = FakeRegistrations(serverRegistrations())
        val presenter = EventRegistrationsPresenter(view, repo, UiStrings(context), scope())
        presenter.load("e1")
        
        presenter.selectAllForPrint(); 

        assertEquals(45, view.selected.size)
    }

    @Test
    fun registrations_countsUseServerStatusTotalsNotAFullFetch() {
        val view = RecordingRegistrationsView()
        val all = serverRegistrations() + reg("Gone", RegistrationStatus.EXITED) + reg("Cancelled", RegistrationStatus.CANCELLED) + reg("NoShow", RegistrationStatus.NO_SHOW)
        val repo = FakeRegistrations(all)
        val presenter = EventRegistrationsPresenter(view, repo, UiStrings(context), scope())
        presenter.load("e1")

        assertEquals(46L, view.counts.total) // Registered 35 + Checked In 11
        assertEquals(11L, view.counts.checkedIn) // ENTERED 10 + EXITED 1
        assertEquals(35L, view.counts.registered)
        assertTrue(repo.countCalls.containsAll(listOf(RegistrationStatus.ENTERED, RegistrationStatus.EXITED, RegistrationStatus.REGISTERED)))
    }

    @Test
    fun registrations_aFailedCountShowsDashesForThatTileOnly() {
        val view = RecordingRegistrationsView()
        val repo = FakeRegistrations(serverRegistrations()).also { it.failStatus = RegistrationStatus.EXITED }
        val presenter = EventRegistrationsPresenter(view, repo, UiStrings(context), scope())
        presenter.load("e1")

        assertNull(view.counts.total)
        assertNull(view.counts.checkedIn)
        assertEquals(35L, view.counts.registered)
    }

    // ------------------------------------------------------------------ staff dashboard

    private class RecordingDashboardView : StaffDashboardContract.View {
        var scans: Int? = -1
        var checkins: Int? = -1
        val messages = mutableListOf<String>()
        override fun renderRecentScans(items: List<TransactionResponse>) = Unit
        override fun updateStats(scans: Int?, checkins: Int?) { this.scans = scans; this.checkins = checkins }
        override fun showMessage(message: String) { messages += message }
        override fun showLoading(isLoading: Boolean) = Unit
        override fun showNotificationBadge(unreadCount: Int) = Unit
    }

    private inner class FakeDashboardRepo(
        val summary: NetworkResult<StaffTodaySummary>,
        val today: NetworkResult<List<TransactionResponse>> = NetworkResult.Success(emptyList()),
    ) : StaffRepository(context) {
        override suspend fun getEvents() = NetworkResult.Success(emptyList<com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse>())
        override suspend fun getMyTodayTransactions() = today
        override suspend fun getMyTodaySummary() = summary
        override suspend fun getMyNotifications(): NetworkResult<List<NotificationResponse>> = NetworkResult.Success(emptyList())
    }

    @Test
    fun dashboard_failedCountsShowUnavailableAndAMessageNotZero() {
        val view = RecordingDashboardView()
        val presenter = StaffDashboardPresenter(view, FakeDashboardRepo(NetworkResult.Error("boom")), UiStrings(context), scope())
        presenter.loadData()

        assertNull(view.scans)
        assertNull(view.checkins)
        assertEquals(1, view.messages.count { it == context.getString(com.thedavelopers.eventqr.R.string.staff_dashboard_stats_unavailable) })
    }

    @Test
    fun dashboard_tilesUseServerSummaryNotTheRecentList() {
        val view = RecordingDashboardView()
        val recent = listOf(tx(UUID.randomUUID(), TransactionType.ENTRY, TransactionResult.APPROVED))
        val presenter = StaffDashboardPresenter(
            view,
            FakeDashboardRepo(NetworkResult.Success(StaffTodaySummary(scannedToday = 42, successfulCheckIns = 17)), NetworkResult.Success(recent)),
            UiStrings(context),
            scope(),
        )
        presenter.loadData()

        assertEquals(42, view.scans)
        assertEquals(17, view.checkins)
        assertTrue(view.messages.isEmpty())
    }
}
