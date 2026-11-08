package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.dashboard.DashboardStats
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class EventsPresenter(
    private var view: EventsContract.View?,
    private val repository: AttendeeRepository,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

    /** The last usable live-registration id set; null until the first successful answer. */
    private var registeredEventIds: Set<UUID>? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    /**
     * [silent] refreshes the list already on screen (e.g. on return from an event's detail): no skeleton,
     * and a failure keeps the current cards instead of replacing them with the error state.
     *
     * Events and registrations load in parallel so the cards never wait on the (potentially paged)
     * registrations call. A fresh [RegistrationsCache] answer skips the refetch; a failed registration
     * load keeps the last good set, and only the first-ever load reports `null` so the `Registered`
     * chip can render a retry state instead of claiming the attendee has no registrations.
     */
    fun loadEvents(silent: Boolean = false) {
        // A visible load already in flight has hidden the list behind the skeleton; it must finish (and show
        // its error state on failure), so a silent refresh never replaces it.
        if (silent && job?.isActive == true) return
        if (!silent) view?.showLoading(true)
        job?.cancel()
        job = scope.launch {
            val eventsDeferred = async { repository.getBrowseEvents() }
            val registrationsDeferred = async {
                val cached = RegistrationsCache.get()
                if (cached != null && RegistrationsCache.isFresh()) {
                    NetworkResult.Success(cached)
                } else {
                    repository.getMyRegistrations()
                }
            }

            when (val result = eventsDeferred.await()) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showEvents(result.data)
                    renderRegisteredEventIds(registrationsDeferred.await())
                }
                is NetworkResult.Error -> {
                    registrationsDeferred.cancel()
                    view?.showLoading(false)
                    if (!silent) view?.showError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderRegisteredEventIds(result: NetworkResult<List<RegistrationResponse>>) {
        when (result) {
            is NetworkResult.Success -> {
                val ids = result.data
                    .filter { DashboardStats.isLive(it.status) }
                    .map { it.eventId }
                    .toSet()
                registeredEventIds = ids
                view?.showRegisteredEventIds(ids)
            }
            is NetworkResult.Error -> {
                // A refresh failure keeps the last good set so rows stay readable; only a
                // first-ever failure reports null for the chip's retry state.
                if (registeredEventIds == null) view?.showRegisteredEventIds(null)
            }
            NetworkResult.Loading -> Unit
        }
    }
}
