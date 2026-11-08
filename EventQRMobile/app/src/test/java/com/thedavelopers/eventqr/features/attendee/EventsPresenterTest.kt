package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/** The silent reload used on return to the events list refreshes counts without a skeleton or an error takeover. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventsPresenterTest {

    @Before
    fun clearCache() {
        RegistrationsCache.clear()
    }

    private fun event(count: Int, id: UUID = UUID.randomUUID()) = AttendeeEventResponse(
        eventId = id, title = "Fest", capacity = 12, currentAttendeeCount = count,
    )

    private fun registration(eventId: UUID, status: RegistrationStatus) = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "attendee@example.com",
        attendeeName = "Attendee",
        status = status,
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

    /** Registered means a live registration: cancelled and no-show rows are not in the set. */
    @Test
    fun registeredEventIds_keepOnlyLiveRegistrations() {
        val view = RecordingView()
        val live = event(0)
        val cancelled = event(0)
        val noShow = event(0)
        val registrations = listOf(
            registration(live.eventId, RegistrationStatus.REGISTERED),
            registration(cancelled.eventId, RegistrationStatus.CANCELLED),
            registration(noShow.eventId, RegistrationStatus.NO_SHOW),
        )

        EventsPresenter(
            view,
            FakeRepo(
                events = NetworkResult.Success(listOf(live, cancelled, noShow)),
                registrations = NetworkResult.Success(registrations),
            ),
        ).loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(setOf(live.eventId)), view.registeredIds)
    }

    /** A failed registration load is reported as null, never an empty set (§6.2). */
    @Test
    fun registrationFailure_reportsNullNotAnEmptySet() {
        val view = RecordingView()

        EventsPresenter(
            view,
            FakeRepo(
                events = NetworkResult.Success(listOf(event(0))),
                registrations = NetworkResult.Error("Offline"),
            ),
        ).loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf<Set<UUID>?>(null), view.registeredIds)
        assertNull(view.registeredIds.single())
    }

    /** A silent refresh keeps the last good set, so the rows stay readable. */
    @Test
    fun silentRegistrationFailure_keepsThePreviousSet() {
        val view = RecordingView()
        val event = event(0)
        val repo = FakeRepo(
            events = NetworkResult.Success(listOf(event)),
            registrations = NetworkResult.Success(
                listOf(registration(event.eventId, RegistrationStatus.REGISTERED)),
            ),
        )
        val presenter = EventsPresenter(view, repo)
        presenter.loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        repo.registrations = NetworkResult.Error("Offline")
        presenter.loadEvents(silent = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(setOf(event.eventId)), view.registeredIds)
    }

    /** A visible pull-to-refresh failure must not null the set either: the Registered rows would vanish. */
    @Test
    fun nonSilentRegistrationFailure_keepsThePreviousSet() {
        val view = RecordingView()
        val event = event(0)
        val repo = FakeRepo(
            events = NetworkResult.Success(listOf(event)),
            registrations = NetworkResult.Success(
                listOf(registration(event.eventId, RegistrationStatus.REGISTERED)),
            ),
        )
        val presenter = EventsPresenter(view, repo)
        presenter.loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        repo.registrations = NetworkResult.Error("Offline")
        presenter.loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(setOf(event.eventId)), view.registeredIds)
    }

    /** The cards render as soon as the events answer, without waiting for the registrations call. */
    @Test
    fun eventsRenderBeforeTheRegistrationsRequestCompletes() {
        val view = RecordingView()
        val event = event(0)
        val registrations = CompletableDeferred<NetworkResult<List<RegistrationResponse>>>()
        val presenter = EventsPresenter(
            view,
            DeferredRegistrationsRepo(NetworkResult.Success(listOf(event)), registrations),
        )

        presenter.loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, view.events.size)
        assertTrue("registrations are still in flight", view.registeredIds.isEmpty())

        registrations.complete(
            NetworkResult.Success(listOf(registration(event.eventId, RegistrationStatus.REGISTERED))),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(setOf(event.eventId)), view.registeredIds)
    }

    /** A fresh shared cache answers without a second (paged) registrations request. */
    @Test
    fun freshRegistrationsCache_skipsTheRegistrationsCall() {
        val view = RecordingView()
        val event = event(0)
        RegistrationsCache.set(listOf(registration(event.eventId, RegistrationStatus.REGISTERED)))
        val repo = FakeRepo(
            events = NetworkResult.Success(listOf(event)),
            registrations = NetworkResult.Error("must not be fetched"),
        )

        EventsPresenter(view, repo).loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(setOf(event.eventId)), view.registeredIds)
        assertEquals(0, repo.registrationCalls)
    }

    /**
     * A register/cancel merge into a cleared cache seeds a partial list. It must not read as fresh
     * or the Registered filter keeps only that one row and hides the attendee's other live ones.
     */
    @Test
    fun partialCacheSeed_isNotFresh_soThePresenterRefetches() {
        val view = RecordingView()
        val justRegistered = event(0)
        val otherLive = event(0)
        RegistrationsCache.addRegistration(
            registration(justRegistered.eventId, RegistrationStatus.REGISTERED),
        )
        assertFalse("a merge into a null cache is not a full answer", RegistrationsCache.isFresh())
        val repo = FakeRepo(
            events = NetworkResult.Success(listOf(justRegistered, otherLive)),
            registrations = NetworkResult.Success(
                listOf(
                    registration(justRegistered.eventId, RegistrationStatus.REGISTERED),
                    registration(otherLive.eventId, RegistrationStatus.REGISTERED),
                ),
            ),
        )

        EventsPresenter(view, repo).loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repo.registrationCalls)
        assertEquals(listOf(setOf(justRegistered.eventId, otherLive.eventId)), view.registeredIds)
    }

    /** A merge into a full list keeps that list's freshness: no extra registrations request. */
    @Test
    fun mergeIntoAFullCache_staysFreshAndIsReused() {
        val view = RecordingView()
        val live = event(0)
        val cancelled = event(0)
        RegistrationsCache.set(listOf(registration(live.eventId, RegistrationStatus.REGISTERED)))
        RegistrationsCache.addRegistration(registration(cancelled.eventId, RegistrationStatus.CANCELLED))
        val repo = FakeRepo(
            events = NetworkResult.Success(listOf(live, cancelled)),
            registrations = NetworkResult.Error("must not be fetched"),
        )

        EventsPresenter(view, repo).loadEvents()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, repo.registrationCalls)
        assertEquals(listOf(setOf(live.eventId)), view.registeredIds)
    }

    private class FakeRepo(
        private val events: NetworkResult<List<AttendeeEventResponse>>,
        var registrations: NetworkResult<List<RegistrationResponse>> = NetworkResult.Success(emptyList()),
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var registrationCalls = 0
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> = events
        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> {
            registrationCalls++
            return registrations
        }
    }

    private class DeferredRegistrationsRepo(
        private val events: NetworkResult<List<AttendeeEventResponse>>,
        private val registrations: CompletableDeferred<NetworkResult<List<RegistrationResponse>>>,
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> = events
        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
            registrations.await()
    }

    /** First call suspends until [first] completes; later calls return [later]. */
    private class QueuedRepo(
        private val first: CompletableDeferred<NetworkResult<List<AttendeeEventResponse>>>,
        private val later: NetworkResult<List<AttendeeEventResponse>>,
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var calls = 0
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> =
            if (calls++ == 0) first.await() else later
        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
            NetworkResult.Success(emptyList())
    }

    private class RecordingView : EventsContract.View {
        val loading = mutableListOf<Boolean>()
        val errors = mutableListOf<String>()
        val events = mutableListOf<List<AttendeeEventResponse>>()
        val registeredIds = mutableListOf<Set<UUID>?>()
        override fun showLoading(isLoading: Boolean) { loading += isLoading }
        override fun showMessage(message: String) = Unit
        override fun showEvents(items: List<AttendeeEventResponse>) { events += items }
        override fun showRegisteredEventIds(ids: Set<UUID>?) { registeredIds += ids }
        override fun showError(message: String) { errors += message }
    }
}
