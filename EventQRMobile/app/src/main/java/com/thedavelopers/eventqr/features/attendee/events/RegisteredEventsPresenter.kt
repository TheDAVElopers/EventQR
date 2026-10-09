package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.dashboard.DashboardStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RegisteredEventsPresenter(
    private var view: RegisteredEventsContract.View?,
    private val repository: AttendeeRepository,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun load() {
        view?.showLoading(true)
        job = scope.launch {
            val regsResult = repository.getMyRegistrations()
            if (regsResult is NetworkResult.Success) {
                view?.showLoading(false)
                view?.showRegisteredEvents(regsResult.data.filter { DashboardStats.isLive(it.status) })
            } else if (regsResult is NetworkResult.Error) {
                view?.showLoading(false)
                view?.showMessage(regsResult.message)
            }
        }
    }
}
