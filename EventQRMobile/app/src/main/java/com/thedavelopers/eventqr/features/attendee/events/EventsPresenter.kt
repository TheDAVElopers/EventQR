package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EventsPresenter(
    private var view: EventsContract.View?,
    private val repository: AttendeeRepository,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun loadEvents() {
        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository.getBrowseEvents()) {
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
