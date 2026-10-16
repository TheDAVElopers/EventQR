package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/** The silent reload used on return to the events list refreshes counts without a skeleton or an error takeover. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventsPresenterTest {

    private fun event(count: Int) = AttendeeEventResponse(
        eventId = UUID.randomUUID(), title = "Fest", capacity = 12, currentAttendeeCount = count,
    )

    @Test
    fun loadEvents_showsSkeletonAndErrorState() {
        val view = RecordingView()

        EventsPresenter(view, FakeRepo(NetworkResult.Error("Offline"))).loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(true, false), view.loading)
        assertEquals(listOf("Offline"), view.errors)
    }

    @Test
    fun silentLoad_successDeliversFreshCountsWithoutSkeleton() {
        val view = RecordingView()

        EventsPresenter(view, FakeRepo(NetworkResult.Success(listOf(event(1))))).loadEvents(silent = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(false), view.loading)
        assertEquals(1, view.events.single().single().currentAttendeeCount)
    }

    @Test
    fun silentLoad_failureKeepsTheCurrentListInsteadOfShowingTheErrorState() {
        val view = RecordingView()

        EventsPresenter(view, FakeRepo(NetworkResult.Error("Offline"))).loadEvents(silent = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(false), view.loading)
        assertTrue(view.errors.isEmpty())
        assertTrue(view.events.isEmpty())
    }

    @Test
    fun silentLoadWhileAVisibleLoadIsInFlight_doesNotHideTheVisibleLoadsErrorState() {
        val view = RecordingView()
        val firstCall = CompletableDeferred<NetworkResult<List<AttendeeEventResponse>>>()
        val repo = QueuedRepo(firstCall, NetworkResult.Error("Silent failure"))
        val presenter = EventsPresenter(view, repo)

        presenter.loadEvents() // skeleton shown, list hidden, request in flight
        shadowOf(Looper.getMainLooper()).idle()
        presenter.loadEvents(silent = true) // e.g. onResume while the first load is still running
        shadowOf(Looper.getMainLooper()).idle()
        firstCall.complete(NetworkResult.Error("Offline"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repo.calls)
        assertEquals(listOf(true, false), view.loading)
        assertEquals(listOf("Offline"), view.errors)
    }

    private class FakeRepo(private val result: NetworkResult<List<AttendeeEventResponse>>) :
        AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> = result
    }

    /** First call suspends until [first] completes; later calls return [later]. */
    private class QueuedRepo(
        private val first: CompletableDeferred<NetworkResult<List<AttendeeEventResponse>>>,
        private val later: NetworkResult<List<AttendeeEventResponse>>,
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var calls = 0
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> =
            if (calls++ == 0) first.await() else later
    }

    private class RecordingView : EventsContract.View {
        val loading = mutableListOf<Boolean>()
        val errors = mutableListOf<String>()
        val events = mutableListOf<List<AttendeeEventResponse>>()
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showMessage(message: String) = Unit
        override fun showEvents(items: List<AttendeeEventResponse>) { events += items }
        override fun showError(message: String) { errors += message }
    }
}
