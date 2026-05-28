package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.attendee.AttendeeRepository
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant

class DashboardPresenter(
    private var view: DashboardContract.View?,
    private val repository: DashboardRepository,
    private val attendeeRepository: AttendeeRepository,
    private val sessionManager: SessionManager,
) {
    private var dashboardJob: Job? = null

    fun attach(view: DashboardContract.View) {
        this.view = view
    }

    fun detach() {
        dashboardJob?.cancel()
        view = null
    }

    fun loadDashboard() {
        view?.updateHeader(sessionManager.getUserRole(), sessionManager.getFullName())
        view?.showLoading(true)
        dashboardJob = MainScope().launch {
            val summaryResult = repository.getSummary()
            val eventsResult = attendeeRepository.getEvents()

            view?.showLoading(false)

            if (summaryResult is NetworkResult.Success) {
                var summary = summaryResult.data

                val upcoming = if (eventsResult is NetworkResult.Success) {
                    val now = Instant.now()
                    eventsResult.data
                        .filter { it.eventEndAt?.isBefore(now) != true }
                        .sortedBy { it.eventStartAt }
                        .take(2)
                        .map { event ->
                            DashboardUpcomingEvent(
                                eventId = event.eventId,
                                title = event.title,
                                location = event.location,
                                category = event.category,
                                eventStartAt = event.eventStartAt,
                                status = if (event.eventStartAt?.isAfter(now) == true) "Upcoming" else "Ongoing",
                                description = event.description,
                                eventEndAt = event.eventEndAt,
                                capacity = event.capacity,
                                currentAttendeeCount = event.currentAttendeeCount
                            )
                        }
                } else {
                    emptyList()
                }

                summary = summary.copy(upcomingEvents = upcoming)
                view?.showSummary(summary)

                if (eventsResult is NetworkResult.Error) {
                    view?.showMessage("Unable to load upcoming events: ${eventsResult.message}")
                }
            } else if (summaryResult is NetworkResult.Error) {
                view?.showError(summaryResult.message)
            }
        }
    }

    fun openSection(title: String, message: String) {
        view?.openSection(title, message)
    }

    fun logout() {
        sessionManager.clearSession()
    }
}