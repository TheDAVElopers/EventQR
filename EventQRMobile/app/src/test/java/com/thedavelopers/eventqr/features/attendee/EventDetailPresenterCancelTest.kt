package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
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

        assertEquals(listOf(msg), view.messages)
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

        assertEquals(listOf("Network problem. Check your connection and try again."), view.messages)
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
        presenter(view, FakeRepo(NetworkResult.Error("x"))).refreshRegistrationStatus(eventId.toString())
        assertEquals(registered.registrationId.toString(), view.cancellable.first())

        for (status in listOf(RegistrationStatus.ENTERED, RegistrationStatus.EXITED, RegistrationStatus.CANCELLED, RegistrationStatus.NO_SHOW)) {
            RegistrationsCache.set(listOf(registration(status)))
            val v = RecordingView(userId = "u1")
            presenter(v, FakeRepo(NetworkResult.Error("x"))).refreshRegistrationStatus(eventId.toString())
            assertNull("status $status", v.cancellable.first())
        }
        // CANCELLED is not shown as an active registration.
        RegistrationsCache.set(listOf(registration(RegistrationStatus.CANCELLED)))
        val v = RecordingView(userId = "u1")
        presenter(v, FakeRepo(NetworkResult.Error("x"))).refreshRegistrationStatus(eventId.toString())
        assertFalse(v.registered.first())
    }

    private class FakeRepo(private val result: NetworkResult<RegistrationResponse>) :
        AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val cancelledIds = mutableListOf<String>()
        override suspend fun cancelRegistration(registrationId: String): NetworkResult<RegistrationResponse> {
            cancelledIds += registrationId
            return result
        }
    }

    private class RecordingView(private val userId: String = "") : EventDetailContract.View {
        val messages = mutableListOf<String>()
        val cancellingStates = mutableListOf<Boolean>()
        val cancellable = mutableListOf<String?>()
        val registered = mutableListOf<Boolean>()
        val cancelledMessages = mutableListOf<String>()
        override fun renderEvent(event: AttendeeEventResponse) = Unit
        override fun updateRegistrationStatus(isRegistered: Boolean) { registered += isRegistered }
        override fun onRegistrationStatusCheckFailed() = Unit
        override fun setCancellableRegistration(registrationId: String?) { cancellable += registrationId }
        override fun showCancelling(isCancelling: Boolean) { cancellingStates += isCancelling }
        override fun onRegistrationCancelled(message: String) { cancelledMessages += message }
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
        for (s in listOf("ACTIVE", "ENDED", "REJECTED")) {
            val status = com.thedavelopers.eventqr.core.api.dto.EventStatus.valueOf(s)
            assertFalse(s, EventDetailPresenter.isCancelWindowOpen(event(future, status), now))
        }
        val cancelled = com.thedavelopers.eventqr.core.api.dto.EventStatus.CANCELLED
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, cancelled), now))
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(now.minusSeconds(60), cancelled), now))
        assertFalse(EventDetailPresenter.isCancelWindowOpen(null, now))
    }

    @Test
    fun registrationEnd_hidesOnlyStrictlyAfterEnd_nullNeverCloses_cancelledEventExempt() {
        val future = now.plusSeconds(3600)
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now.plusSeconds(60)), now))
        // Boundary matches the server (and the Register button): closed only strictly after the end.
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now), now))
        assertFalse(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now.minusSeconds(1)), now))
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = null), now))
        val cancelled = com.thedavelopers.eventqr.core.api.dto.EventStatus.CANCELLED
        assertTrue(EventDetailPresenter.isCancelWindowOpen(event(future, regClose = now.minusSeconds(1), status = cancelled), now))
    }
}
