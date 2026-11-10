package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointTransactionResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventDetailPresenterCancelTest {

    private val eventId = UUID.randomUUID()

    @After
    fun tearDown() = RegistrationsCache.clear()

    private fun registration(status: RegistrationStatus) = RegistrationResponse(
        registrationId = UUID.randomUUID(), eventId = eventId, attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com", attendeeName = "A", status = status,
    )

    private fun presenter(view: RecordingView, repo: FakeRepo) =
        EventDetailPresenter(view, repo, UiStrings(ApplicationProvider.getApplicationContext()))

    @Test
    fun cancel_success_updatesCacheAndView() {
        val reg = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(reg))
        val cancelled = reg.copy(status = RegistrationStatus.CANCELLED)
        val repo = FakeRepo(NetworkResult.Success(cancelled, "Registration cancelled"))
        val view = RecordingView()

        presenter(view, repo).cancelRegistration(reg.registrationId.toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(reg.registrationId.toString()), repo.cancelledIds)
        assertEquals(listOf(true, false), view.cancellingStates)
        assertEquals(listOf<String?>(null), view.cancellable)
        assertEquals(listOf(false), view.registered)
        assertEquals(1, view.cancelledMessages.size)
        assertTrue(view.messages.isEmpty())
        assertEquals(RegistrationStatus.CANCELLED, RegistrationsCache.get()!!.single().status)
    }

    @Test
    fun cancel_conflict_showsServerMessageAndKeepsRegistration() {
        val msg = "You can't cancel after check-in"
        val reg = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(reg))
        val repo = FakeRepo(NetworkResult.Error("Conflict", serverMessage = msg))
        val view = RecordingView()

        presenter(view, repo).cancelRegistration(reg.registrationId.toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(msg), view.failures)
        assertTrue(view.messages.isEmpty())
        assertEquals(listOf(true, false), view.cancellingStates)
        assertTrue(view.cancelledMessages.isEmpty())
        assertTrue(view.cancellable.isEmpty())
        // Unconfirmed outcome: the cache is invalidated so the next read refetches the truth.
        assertNull(RegistrationsCache.get())
    }

    @Test
    fun cancel_errorWithoutServerMessage_fallsBackToErrorMessage() {
        val repo = FakeRepo(NetworkResult.Error("Network problem. Check your connection and try again."))
        val view = RecordingView()

        presenter(view, repo).cancelRegistration(UUID.randomUUID().toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("Network problem. Check your connection and try again."), view.failures)
    }

    @Test
    fun cancel_ignoresSecondTapWhileInFlight() {
        val repo = FakeRepo(NetworkResult.Error("x", serverMessage = "x"))
        val view = RecordingView()
        val p = presenter(view, repo)
        val id = UUID.randomUUID().toString()

        p.cancelRegistration(id)
        p.cancelRegistration(id)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repo.cancelledIds.size)
    }

    @Test
    fun cachedRegistration_exposesCancelOnlyWhenStatusIsRegistered() {
        val registered = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(registered))
        val view = RecordingView(userId = "u1")
        presenter(view, FakeRepo()).refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(registered.registrationId.toString(), view.cancellable.last())

        for (status in listOf(RegistrationStatus.ENTERED, RegistrationStatus.EXITED, RegistrationStatus.CANCELLED, RegistrationStatus.NO_SHOW)) {
            RegistrationsCache.set(listOf(registration(status)))
            val v = RecordingView(userId = "u1")
            presenter(v, FakeRepo()).refreshRegistrationStatus(eventId.toString())
            shadowOf(Looper.getMainLooper()).idle()
            assertNull("status $status", v.cancellable.last())
        }
        // CANCELLED is not shown as an active registration.
        RegistrationsCache.set(listOf(registration(RegistrationStatus.CANCELLED)))
        val v = RecordingView(userId = "u1")
        presenter(v, FakeRepo()).refreshRegistrationStatus(eventId.toString())
        assertFalse(v.registered.first())
    }

    @Test
    fun recordedPointActivity_hidesCancel() {
        val registered = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(registered))
        val repo = FakeRepo().apply {
            pointTransactions = NetworkResult.Success(listOf(pointTransaction()))
        }
        val view = RecordingView(userId = "u1")

        presenter(view, repo).refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(view.cancellable.isNotEmpty())
        assertTrue(view.cancellable.all { it == null })
    }

    @Test
    fun failedPointRead_failsClosedAndHidesCancel() {
        val registered = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(registered))
        val repo = FakeRepo().apply {
            pointTransactions = NetworkResult.Error("offline")
        }
        val view = RecordingView(userId = "u1")

        presenter(view, repo).refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(view.cancellable.isNotEmpty())
        assertTrue(view.cancellable.all { it == null })
    }

    @Test
    fun failedScanRead_failsClosedAndHidesCancel() {
        val registered = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(registered))
        val repo = FakeRepo().apply {
            scans = NetworkResult.Error("offline")
        }
        val view = RecordingView(userId = "u1")

        presenter(view, repo).refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(view.cancellable.isNotEmpty())
        assertTrue(view.cancellable.all { it == null })
    }

    @Test
    fun failedCancel_reverifiesActivityOnTheNextResume() {
        val registered = registration(RegistrationStatus.REGISTERED)
        // A stale cache forces the registration read, so the refresh really reaches the activity check.
        RegistrationsCache.clear()
        RegistrationsCache.addRegistration(registered)
        val repo = FakeRepo(NetworkResult.Error("Conflict", serverMessage = "already checked in")).apply {
            registrations = NetworkResult.Success(listOf(registered))
        }
        val view = RecordingView(userId = "u1")
        val presenter = presenter(view, repo)

        presenter.refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, repo.pointReads)
        assertEquals(1, repo.scanReads)
        assertEquals(registered.registrationId.toString(), view.cancellable.last())

        presenter.cancelRegistration(registered.registrationId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("already checked in"), view.failures)

        // The server refused, so the old verification is stale: the next resume re-reads activity.
        presenter.refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, repo.pointReads)
        assertEquals(2, repo.scanReads)
        assertEquals(registered.registrationId.toString(), view.cancellable.last())
    }

    @Test
    fun verifiedRegistration_isNotRecheckedOnTheNextResume() {
        val registered = registration(RegistrationStatus.REGISTERED)
        // A stale cache (no fetchedAt) forces the registration read on every refresh, so only the
        // short-circuit inside applyCancellableRegistration keeps the activity calls at one.
        RegistrationsCache.clear()
        RegistrationsCache.addRegistration(registered)
        val repo = FakeRepo().apply { registrations = NetworkResult.Success(listOf(registered)) }
        val view = RecordingView(userId = "u1")
        val presenter = presenter(view, repo)

        presenter.refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        presenter.refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repo.pointReads)
        assertEquals(1, repo.scanReads)
        assertEquals(registered.registrationId.toString(), view.cancellable.last())
    }

    @Test
    fun approvedScan_hidesCancel_butRejectedScanDoesNot() {
        val registered = registration(RegistrationStatus.REGISTERED)
        RegistrationsCache.set(listOf(registered))
        val repo = FakeRepo().apply {
            scans = NetworkResult.Success(listOf(scan(registered.registrationId.toString(), TransactionResult.APPROVED)))
        }
        val blocked = RecordingView(userId = "u1")
        presenter(blocked, repo).refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(blocked.cancellable.isNotEmpty())
        assertTrue(blocked.cancellable.all { it == null })

        repo.scans = NetworkResult.Success(listOf(scan(registered.registrationId.toString(), TransactionResult.REJECTED)))
        val allowed = RecordingView(userId = "u1")
        presenter(allowed, repo).refreshRegistrationStatus(eventId.toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(registered.registrationId.toString(), allowed.cancellable.last())
    }

    private fun pointTransaction() = PointTransactionResponse(
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        sourceTransactionId = UUID.randomUUID(),
        pointsChanged = 10,
    )

    private fun scan(registrationId: String, result: TransactionResult) = TransactionResponse(
        transactionId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        registrationId = UUID.fromString(registrationId),
        qrCredentialId = UUID.randomUUID(),
        scanPurposeId = UUID.randomUUID(),
        transactionType = TransactionType.ENTRY,
        transactionResult = result,
        pointsDelta = 10,
    )

    private class FakeRepo(
        private val result: NetworkResult<RegistrationResponse> = NetworkResult.Error("x"),
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val cancelledIds = mutableListOf<String>()
        var registrations: NetworkResult<List<RegistrationResponse>> = NetworkResult.Error("x")
        var pointTransactions: NetworkResult<List<PointTransactionResponse>> = NetworkResult.Success(emptyList())
        var scans: NetworkResult<List<TransactionResponse>> = NetworkResult.Success(emptyList())
        var pointReads = 0
        var scanReads = 0
        override suspend fun cancelRegistration(registrationId: String): NetworkResult<RegistrationResponse> {
            cancelledIds += registrationId
            return result
        }
        override suspend fun getMyRegistrations() = registrations
        override suspend fun getMyPointTransactions(eventId: String): NetworkResult<List<PointTransactionResponse>> {
            pointReads++
            return pointTransactions
        }
        override suspend fun getMyEventTransactions(eventId: String): NetworkResult<List<TransactionResponse>> {
            scanReads++
            return scans
        }
    }

    private class RecordingView(private val userId: String = "") : EventDetailContract.View {
        val messages = mutableListOf<String>()
        val cancellingStates = mutableListOf<Boolean>()
        val cancellable = mutableListOf<String?>()
        val registered = mutableListOf<Boolean>()
        val cancelledMessages = mutableListOf<String>()
        val failures = mutableListOf<String>()
        override fun renderEvent(event: AttendeeEventResponse) = Unit
        override fun updateRegistrationStatus(isRegistered: Boolean) { registered += isRegistered }
        override fun onRegistrationStatusCheckFailed() = Unit
        override fun setCancellableRegistration(registrationId: String?) { cancellable += registrationId }
        override fun showCancelling(isCancelling: Boolean) { cancellingStates += isCancelling }
        override fun onRegistrationCancelled(message: String) { cancelledMessages += message }
        override fun showCancelFailure(message: String) { failures += message }
        override fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String, phoneNumber: String) = Unit
        override fun getSessionUserId(): String? = userId
        override fun getSessionEmail(): String = ""
        override fun getSessionFullName(): String = ""
        override fun getSessionPhone(): String = ""
        override fun showLoading(isLoading: Boolean) = Unit
        override fun showMessage(message: String) { messages += message }
    }
}

class EventDetailCancelWindowTest {
    private val now = java.time.Instant.parse("2026-10-10T00:00:00Z")

    private fun event(
        start: java.time.Instant?,
        status: com.thedavelopers.eventqr.core.api.dto.EventStatus? = com.thedavelopers.eventqr.core.api.dto.EventStatus.APPROVED,
        regClose: java.time.Instant? = null,
    ) = AttendeeEventResponse(eventId = UUID.randomUUID(), title = "E", eventStartAt = start, registrationCloseAt = regClose, status = status)

    @Test
    fun open_onlyBeforeStartAndForNonTerminalStatus() {
        val future = now.plusSeconds(3600)
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future), now))
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, null), now))
        assertFalse(EventDetailPresenter.isCancelWindowOpen(event(now), now))
        assertFalse(EventDetailPresenter.isCancelWindowOpen(event(now.minusSeconds(60)), now))
        for (s in listOf("ACTIVE", "ENDED", "REJECTED", "CANCELLED")) {
            val status = com.thedavelopers.eventqr.core.api.dto.EventStatus.valueOf(s)
            assertFalse(s, EventDetailPresenter.isCancelWindowOpen(event(future, status), now))
        }
        assertFalse(EventDetailPresenter.isCancelWindowOpen(null, now))
    }

    @Test
    fun registrationEnd_hidesOnlyStrictlyAfterEnd_nullNeverCloses() {
        val future = now.plusSeconds(3600)
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now.plusSeconds(60)), now))
        // Boundary matches the server (and the Register button): closed only strictly after the end.
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now), now))
        assertFalse(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now.minusSeconds(1)), now))
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = null), now))
        val cancelled = com.thedavelopers.eventqr.core.api.dto.EventStatus.CANCELLED
        // A CANCELLED event is refused by the server too, so it is hidden even while registration is nominally open.
        assertFalse(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now.plusSeconds(60), status = com.thedavelopers.eventqr.core.api.dto.EventStatus.CANCELLED), now))
    }

    @Test
    fun primaryAction_isEventCancelledOnlyForCancelledStatus() {
        val future = now.plusSeconds(3600)
        assertTrue(EventDetailPresenter.isEventCancelled(event(future, com.thedavelopers.eventqr.core.api.dto.EventStatus.CANCELLED)))
        for (s in com.thedavelopers.eventqr.core.api.dto.EventStatus.values().filter { it.name != "CANCELLED" }) {
            assertFalse(s.name, EventDetailPresenter.isEventCancelled(event(future, s)))
        }
        assertFalse(EventDetailPresenter.isEventCancelled(event(future, null)))
        assertFalse(EventDetailPresenter.isEventCancelled(null))
    }
}

class EventDetailCancelActivityTest {
    private val registrationId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    private fun pointTransaction() = PointTransactionResponse(
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        sourceTransactionId = UUID.randomUUID(),
        pointsChanged = 5,
    )

    private fun scan(result: TransactionResult, registration: UUID = registrationId) = TransactionResponse(
        transactionId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        registrationId = registration,
        qrCredentialId = UUID.randomUUID(),
        scanPurposeId = UUID.randomUUID(),
        transactionType = TransactionType.ENTRY,
        transactionResult = result,
        pointsDelta = 0,
    )

    @Test
    fun anyPointTransactionBlocks() {
        assertTrue(
            EventDetailPresenter.hasRecordedActivity(
                registrationId.toString(),
                listOf(pointTransaction()),
                emptyList(),
            ),
        )
    }

    @Test
    fun onlyApprovedScanOfThisRegistrationBlocks() {
        val id = registrationId.toString()
        assertTrue(EventDetailPresenter.hasRecordedActivity(id, emptyList(), listOf(scan(TransactionResult.APPROVED))))
        assertFalse(EventDetailPresenter.hasRecordedActivity(id, emptyList(), listOf(scan(TransactionResult.REJECTED))))
        assertFalse(
            EventDetailPresenter.hasRecordedActivity(
                id,
                emptyList(),
                listOf(scan(TransactionResult.APPROVED, registration = UUID.randomUUID())),
            ),
        )
        assertFalse(EventDetailPresenter.hasRecordedActivity(id, emptyList(), emptyList()))
    }
}
