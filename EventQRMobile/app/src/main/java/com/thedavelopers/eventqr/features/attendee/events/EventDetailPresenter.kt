package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.Validators
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EventDetailPresenter(
    private var view: EventDetailContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadEventDetails(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            val result = repository.getEvent(eventId)
            view?.showLoading(false)
            when (result) {
                is NetworkResult.Success -> {
                    view?.renderEvent(result.data)
                    checkRegistrationStatus(eventId)
                }
                is NetworkResult.Error -> {
                    view?.showMessage("Unable to load event details: ${result.message}")
                }
                else -> Unit
            }
        }
    }

    private fun checkRegistrationStatus(eventId: String) {
        val userId = view?.getSessionUserId().orEmpty()
        if (userId.isBlank()) {
            return
        }

        kotlinx.coroutines.MainScope().launch {
            val result = repository.getRegistrationsByEvent(eventId)
            if (result is NetworkResult.Success) {
                val isRegistered = result.data.any { it.attendeeUserId.toString() == userId }
                view?.updateRegistrationStatus(isRegistered)
            }
        }
    }

    fun registerForEvent(eventId: String, eventTitle: String) {
        val email = view?.getSessionEmail().orEmpty()
        val fullName = view?.getSessionFullName().orEmpty()
        val phoneNumber = view?.getSessionPhone().orEmpty()
        if (!Validators.isValidEmail(email) || !Validators.isNonEmpty(fullName)) {
            view?.showMessage("Open registration to enter attendee details")
            view?.openRegistration(eventId, eventTitle, email, fullName, phoneNumber)
            return
        }
        view?.openRegistration(eventId, eventTitle, email, fullName, phoneNumber)
    }
}
