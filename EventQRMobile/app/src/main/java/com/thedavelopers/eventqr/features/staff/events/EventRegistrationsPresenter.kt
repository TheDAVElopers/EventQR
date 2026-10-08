package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EventRegistrationsPresenter(
    private var view: EventRegistrationsContract.View?,
    private val repository: StaffRepository,
    private val strings: UiStrings,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String) {
        if (eventId.isBlank()) {
            view?.showMessage(strings.get(R.string.event_registrations_select_an_assigned_event_first))
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getRegistrationsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderRegistrations(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}
