package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EventDetailPresenter(
    private var view: EventDetailContract.View?,
    private val repository: AttendeeRepository,
    private val strings: UiStrings,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

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
}
