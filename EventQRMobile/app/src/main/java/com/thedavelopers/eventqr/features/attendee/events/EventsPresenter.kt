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

    /**
     * [silent] refreshes the list already on screen (e.g. on return from an event's detail): no skeleton,
     * and a failure keeps the current cards instead of replacing them with the error state.
     */
    fun loadEvents(silent: Boolean = false) {
        // A visible load already in flight has hidden the list behind the skeleton; it must finish (and show
        // its error state on failure), so a silent refresh never replaces it.
        if (silent && job?.isActive == true) return
        if (!silent) view?.showLoading(true)
        job?.cancel()
        job = scope.launch {
            when (val result = repository.getBrowseEvents()) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showEvents(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    if (!silent) view?.showError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}
