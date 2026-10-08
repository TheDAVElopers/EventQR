package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.PagedAccumulator
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import kotlinx.coroutines.CoroutineScope
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EventRegistrationsPresenter(
    private var view: EventRegistrationsContract.View?,
    private val repository: StaffRepository,
    private val strings: UiStrings,
    private val scope: CoroutineScope = MainScope(),
    private val searchDebounceMs: Long = SEARCH_DEBOUNCE_MS,
) {
    private var pageJob: Job? = null
    private var countsJob: Job? = null
    private var searchJob: Job? = null
    private var selectJob: Job? = null

    private val pages = PagedAccumulator<RegistrationResponse, java.util.UUID> { it.registrationId }
    private var eventId: String = ""
    private var query: String = ""
    private var eventTotal: Long? = null
    private var counts = RegistrationCounts()

    fun detach() {
        pageJob?.cancel()
        countsJob?.cancel()
        searchJob?.cancel()
        selectJob?.cancel()
        view = null
    }

    /** Loads the first page for [eventId] and refreshes the header counts. */
    fun load(eventId: String, query: String = "") {
        if (eventId.isBlank()) {
            view?.showMessage(strings.get(R.string.event_registrations_select_an_assigned_event_first))
            return
        }
        this.eventId = eventId
        this.query = query.trim()
        eventTotal = null
        counts = RegistrationCounts()
        view?.renderCounts(counts)
        view?.showLoading(true)
        loadCounts()
        restartPaging()
    }

    /** Debounced server-side search; resets paging to page 0. */
    fun onQueryChanged(newQuery: String) {
        val normalized = newQuery.trim()
        searchJob?.cancel()
        if (eventId.isBlank() || normalized == query) return
        searchJob = scope.launch {
            delay(searchDebounceMs)
            query = normalized
            restartPaging()
        }
    }

    fun loadMore() {
        if (eventId.isBlank()) return
        requestPage()
    }

    /** Retries the page that failed. */
    fun retry() {
        pages.retry()
        requestPage()
    }

    /** Fetches every registration matching the current query so print selection reaches all of them. */
    fun selectAllForPrint() {
        if (eventId.isBlank()) return
        selectJob?.cancel()
        view?.showLoading(true)
        selectJob = scope.launch {
            when (val result = repository.getAllRegistrations(eventId, query)) {
                is NetworkResult.Success -> view?.selectAllForPrint(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    private fun restartPaging() {
        pageJob?.cancel()
        pages.reset()
        requestPage()
    }

    private fun requestPage() {
        val ticket = pages.begin() ?: return
        val requestedQuery = query
        pageJob = scope.launch {
            when (val result = repository.getRegistrationsPage(eventId, requestedQuery, ticket.page)) {
                is NetworkResult.Success -> {
                    if (pages.onSuccess(ticket, result.data)) {
                        if (requestedQuery.isEmpty() && eventTotal == null) {
                            eventTotal = result.data.totalElements
                        }
                        view?.renderRegistrations(pages.items, requestedQuery.isNotEmpty())
                    }
                }
                is NetworkResult.Error -> {
                    if (pages.onFailure(ticket)) {
                        if (pages.isEmpty) view?.renderRegistrations(emptyList(), requestedQuery.isNotEmpty())
                        view?.showLoadMoreError(result.message)
                    }
                }
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }

    /** Three small server-side count requests; each tile fills in (or stays "--") independently. */
    private fun loadCounts() {
        countsJob?.cancel()
        val id = eventId
        countsJob = scope.launch {
            val entered = async { repository.countRegistrations(id, RegistrationStatus.ENTERED) }
            val exited = async { repository.countRegistrations(id, RegistrationStatus.EXITED) }
            val registered = async { repository.countRegistrations(id, RegistrationStatus.REGISTERED) }
            val enteredValue = (entered.await() as? NetworkResult.Success)?.data
            val exitedValue = (exited.await() as? NetworkResult.Success)?.data
            val registeredValue = (registered.await() as? NetworkResult.Success)?.data
            val checkedIn = if (enteredValue != null && exitedValue != null) enteredValue + exitedValue else null
            // Total counts only registrations that count as registered (excludes CANCELLED / NO_SHOW),
            // so it is always Registered + Checked In.
            publishCounts(
                RegistrationCounts(
                    total = if (checkedIn != null && registeredValue != null) checkedIn + registeredValue else null,
                    checkedIn = checkedIn,
                    registered = registeredValue,
                )
            )
        }
    }

    private fun publishCounts(newCounts: RegistrationCounts) {
        counts = newCounts
        view?.renderCounts(newCounts)
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
