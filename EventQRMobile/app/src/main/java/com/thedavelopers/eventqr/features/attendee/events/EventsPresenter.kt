package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EventsPresenter(
    private var view: EventsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showEvents(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}
