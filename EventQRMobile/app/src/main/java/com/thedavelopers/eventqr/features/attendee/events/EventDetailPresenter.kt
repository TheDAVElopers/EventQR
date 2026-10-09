package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

class EventDetailPresenter(
    private var view: EventDetailContract.View?,
    private val repository: AttendeeRepository,
    private val strings: UiStrings,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null
    private var cancelling = false

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun loadEventDetails(eventId: String) {
        view?.showLoading(true)
        job = scope.launch {
            checkRegistrationStatus(eventId)
            val result = repository.getEvent(eventId)
            view?.showLoading(false)
            when (result) {
                is NetworkResult.Success -> {
                    view?.renderEvent(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showMessage(strings.get(R.string.event_detail_unable_to_load_event_details, result.message))
                }
                else -> Unit
            }
        }
    }

    // Public re-entry point so the View can re-sync registration state on resume without
    // reloading the whole event payload.
    fun refreshRegistrationStatus(eventId: String) {
        checkRegistrationStatus(eventId)
    }

    private fun checkRegistrationStatus(eventId: String) {
        val userId = view?.getSessionUserId().orEmpty()
        if (userId.isBlank()) {
            return
        }

        val cached = RegistrationsCache.get()
        if (cached != null) {
            view?.updateRegistrationStatus(isRegisteredIn(cached, eventId))
            view?.setCancellableRegistration(cancellableIdIn(cached, eventId))
        }
        if (cached != null && RegistrationsCache.isFresh()) {
            return
        }

        scope.launch {
            val cachedRegistered = cached?.let { isRegisteredIn(it, eventId) }
            val cachedCancellableId = cached?.let { cancellableIdIn(it, eventId) }
            val result = repository.getMyRegistrations()
            when (result) {
                is NetworkResult.Success -> {
                    val freshRegistered = isRegisteredIn(result.data, eventId)
                    if (cachedRegistered != freshRegistered) {
                        view?.updateRegistrationStatus(freshRegistered)
                    }
                    val freshCancellableId = cancellableIdIn(result.data, eventId)
                    if (cachedCancellableId != freshCancellableId) {
                        view?.setCancellableRegistration(freshCancellableId)
                    }
                }
                is NetworkResult.Error -> {
                    if (cached == null) {
                        view?.onRegistrationStatusCheckFailed()
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun isRegisteredIn(items: List<RegistrationResponse>, eventId: String): Boolean {
        return items.any {
            it.eventId.toString() == eventId &&
                it.status != RegistrationStatus.CANCELLED &&
                it.status != RegistrationStatus.NO_SHOW
        }
    }

    // Only a REGISTERED registration can be cancelled; the backend rejects ENTERED/EXITED/NO_SHOW (CANCELLED is moot).
    private fun cancellableIdIn(items: List<RegistrationResponse>, eventId: String): String? =
        items.firstOrNull { it.eventId.toString() == eventId && it.status == RegistrationStatus.REGISTERED }
            ?.registrationId?.toString()

    fun cancelRegistration(registrationId: String) {
        if (cancelling) return
        cancelling = true
        view?.showCancelling(true)
        scope.launch {
            var succeeded = false
            try {
                val result = repository.cancelRegistration(registrationId)
                cancelling = false
                view?.showCancelling(false)
                when (result) {
                    is NetworkResult.Success -> {
                        succeeded = true
                        // Keep the shared cache in step so other screens do not briefly show the stale registration.
                        RegistrationsCache.addRegistration(result.data)
                        view?.setCancellableRegistration(null)
                        view?.updateRegistrationStatus(false)
                        view?.onRegistrationCancelled(strings.get(R.string.event_detail_registration_cancelled))
                    }
                    is NetworkResult.Error -> {
                        // The 409 message ("already checked in", "event already started", ...) is user readable as-is.
                        val message = result.serverMessage?.takeIf { it.isNotBlank() }
                            ?: result.message.takeIf { it.isNotBlank() }
                            ?: strings.get(R.string.event_detail_cancel_registration_failed)
                        view?.showMessage(message)
                    }
                    NetworkResult.Loading -> Unit
                }
            } finally {
                // If the screen was destroyed mid-request the server may still have cancelled; without a confirmed
                // result the cached REGISTERED row cannot be trusted, so force the next read to refetch.
                if (!succeeded) RegistrationsCache.clear()
            }
        }
    }

    fun registerForEvent(eventId: String, eventTitle: String) {
        val email = view?.getSessionEmail().orEmpty()
        val fullName = view?.getSessionFullName().orEmpty()
        val phoneNumber = view?.getSessionPhone().orEmpty()
        if (!Validators.isValidEmail(email) || !Validators.isNonEmpty(fullName)) {
            view?.showMessage(strings.get(R.string.event_detail_open_registration_to_enter_attendee_details))
            view?.openRegistration(eventId, eventTitle, email, fullName, phoneNumber)
            return
        }
        view?.openRegistration(eventId, eventTitle, email, fullName, phoneNumber)
    }

    companion object {
        /**
         * Client-side mirror of the backend rule (the server stays the authority). A CANCELLED event can always be
         * left, so it skips the time rules. Otherwise cancelling is allowed only while registration is open (same
         * convention as register(): closed strictly after registrationCloseAt, a null close never closes), before
         * the event starts, and not while the event is ACTIVE / ENDED / REJECTED. An unknown event hides the action.
         */
        fun isCancelWindowOpen(event: AttendeeEventResponse?, now: Instant = Instant.now()): Boolean {
            if (event == null) return false
            if (event.status == EventStatus.CANCELLED) return true
            val registrationClosed = event.registrationCloseAt?.let { now.isAfter(it) } == true
            val started = event.eventStartAt?.let { !it.isAfter(now) } == true
            val blocked = event.status == EventStatus.ACTIVE ||
                event.status == EventStatus.ENDED ||
                event.status == EventStatus.REJECTED
            return !registrationClosed && !started && !blocked
        }
    }
}
