package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointTransactionResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
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
    private var activityJob: Job? = null
    private var cancelling = false
    /** The registration id whose activity check passed; null while unverified. */
    private var verifiedCancellableId: String? = null

    fun detach() {
        job?.cancel()
        activityJob?.cancel()
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
            applyCancellableRegistration(cached, eventId)
        }
        if (cached != null && RegistrationsCache.isFresh()) {
            return
        }

        scope.launch {
            val cachedRegistered = cached?.let { isRegisteredIn(it, eventId) }
            val result = repository.getMyRegistrations()
            when (result) {
                is NetworkResult.Success -> {
                    val freshRegistered = isRegisteredIn(result.data, eventId)
                    if (cachedRegistered != freshRegistered) {
                        view?.updateRegistrationStatus(freshRegistered)
                    }
                    applyCancellableRegistration(result.data, eventId)
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

    /**
     * Only a REGISTERED registration can be cancelled, and the backend refuses the cancel once any
     * point transaction or APPROVED scan exists for it. Offer the action only after both activity
     * reads pass; an unverifiable registration hides it rather than leading to a guaranteed error.
     */
    private fun applyCancellableRegistration(items: List<RegistrationResponse>, eventId: String) {
        // The pass id is every live registration, independent of the stricter cancel rules below:
        // a checked-in or exited attendee still owns a pass (§6.2).
        view?.setRegisteredId(registeredIdIn(items, eventId))
        val registrationId = cancellableIdIn(items, eventId)
        activityJob?.cancel()
        if (registrationId == null) {
            verifiedCancellableId = null
            view?.setCancellableRegistration(null)
            return
        }
        if (registrationId != verifiedCancellableId) {
            // Never offer an action the server may reject while the rule is still unknown.
            view?.setCancellableRegistration(null)
        } else {
            // This exact registration already passed both activity reads: keep the button and skip
            // repeating the two calls on every resume.
            view?.setCancellableRegistration(registrationId)
            return
        }
        activityJob = scope.launch {
            val pointTransactions = repository.getMyPointTransactions(eventId)
            val scans = repository.getMyEventTransactions(eventId)
            val eligible = pointTransactions is NetworkResult.Success &&
                scans is NetworkResult.Success &&
                !hasRecordedActivity(registrationId, pointTransactions.data, scans.data)
            verifiedCancellableId = if (eligible) registrationId else null
            view?.setCancellableRegistration(verifiedCancellableId)
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

    /** The pass id: the same live rows [isRegisteredIn] counts, in any status. */
    private fun registeredIdIn(items: List<RegistrationResponse>, eventId: String): String? =
        items.firstOrNull {
            it.eventId.toString() == eventId &&
                it.status != RegistrationStatus.CANCELLED &&
                it.status != RegistrationStatus.NO_SHOW
        }?.registrationId?.toString()

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
                        // The cancelled row is no longer a live registration, so its pass is gone too.
                        view?.setRegisteredId(null)
                        view?.updateRegistrationStatus(false)
                        view?.onRegistrationCancelled(strings.get(R.string.event_detail_registration_cancelled))
                    }
                    is NetworkResult.Error -> {
                        // The 409 message ("already checked in", "event already started", ...) is user readable as-is.
                        val message = result.serverMessage?.takeIf { it.isNotBlank() }
                            ?: result.message.takeIf { it.isNotBlank() }
                            ?: strings.get(R.string.event_detail_cancel_registration_failed)
                        // The server refused (e.g. a scan landed): the earlier activity check no longer
                        // proves the cancel is allowed, so the next resume must re-read it.
                        verifiedCancellableId = null
                        view?.showCancelFailure(message)
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
        /** A cancelled event can no longer be registered for, so its primary action is a disabled "Event cancelled". */
        fun isEventCancelled(event: AttendeeEventResponse?): Boolean = event?.status == EventStatus.CANCELLED

        /**
         * Client-side mirror of the backend rule (the server stays the authority). Cancelling is allowed only
         * while registration is open (same convention as register(): closed strictly after registrationCloseAt, a
         * null close never closes), before the event starts, and not while the event is ACTIVE / ENDED / REJECTED /
         * CANCELLED. An unknown event hides the action.
         */
        fun isCancelWindowOpen(event: AttendeeEventResponse?, now: Instant = Instant.now()): Boolean {
            if (event == null) return false
            val registrationClosed = event.registrationCloseAt?.let { now.isAfter(it) } == true
            val started = event.eventStartAt?.let { !it.isAfter(now) } == true
            val blocked = event.status == EventStatus.ACTIVE ||
                event.status == EventStatus.ENDED ||
                event.status == EventStatus.REJECTED ||
                event.status == EventStatus.CANCELLED
            return !registrationClosed && !started && !blocked
        }

        /**
         * Mirrors the backend's activity rules for an attendee self-cancel: any point transaction
         * for the event (earned, spent or manually adjusted) blocks it, as does any APPROVED scan
         * of this registration (including a 0-point tracking scan). Rejected scans do not.
         */
        fun hasRecordedActivity(
            registrationId: String,
            pointTransactions: List<PointTransactionResponse>,
            scans: List<TransactionResponse>,
        ): Boolean = pointTransactions.isNotEmpty() ||
            scans.any {
                it.registrationId.toString() == registrationId &&
                    it.transactionResult == TransactionResult.APPROVED
            }
    }
}
