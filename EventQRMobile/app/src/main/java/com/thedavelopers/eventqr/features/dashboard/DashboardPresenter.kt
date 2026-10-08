package com.thedavelopers.eventqr.features.dashboard

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.attendee.AttendeeRepository
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.Instant

class DashboardPresenter(
    private var view: DashboardContract.View?,
    private val repository: DashboardRepository,
    private val attendeeRepository: AttendeeRepository,
    private val sessionManager: SessionManager,
    private val strings: UiStrings,
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
            val currentUserDeferred = async { repository.getCurrentUser() }
            val summaryDeferred = async { repository.getSummary() }
            val eventsDeferred = async {
                val browse = attendeeRepository.getBrowseEvents()
                if (browse is NetworkResult.Success && browse.data.isNotEmpty()) {
                    browse
                } else {
                    val visible = attendeeRepository.getEvents()
                    if (visible is NetworkResult.Success && visible.data.isNotEmpty()) {
                        visible
                    } else if (browse is NetworkResult.Success) {
                        browse
                    } else {
                        visible
                    }
                }
            }
            val registrationsDeferred = async { attendeeRepository.getMyRegistrations() }

            val currentUserResult = currentUserDeferred.await()
            if (currentUserResult is NetworkResult.Success) {
                val user = currentUserResult.data
                sessionManager.saveRole(user.role)
                sessionManager.updateProfile(user.fullName, user.phoneNumber, user.email)
                view?.updateHeader(user.role.name, user.fullName)
            }

            val summaryResult = summaryDeferred.await()
            val eventsResult = eventsDeferred.await()
            val registrationsResult = registrationsDeferred.await()

            view?.showLoading(false)

            val now = Instant.now()
            val events = if (eventsResult is NetworkResult.Success) eventsResult.data else emptyList()
            val registrations = if (registrationsResult is NetworkResult.Success) registrationsResult.data else emptyList()

            val activeRegistrations = registrations
                .filter { it.status != RegistrationStatus.CANCELLED && it.status != RegistrationStatus.NO_SHOW }
            val registeredEventIds = activeRegistrations.map { it.eventId }.toSet()

            val mappedEvents = events.map { event ->
                val status = computeEventStatus(event, now)
                DashboardUpcomingEvent(
                    eventId = event.eventId,
                    title = event.title,
                    location = event.location,
                    category = event.category,
                    eventStartAt = event.eventStartAt,
                    status = status,
                    description = event.description,
                    eventEndAt = event.eventEndAt,
                    capacity = event.capacity,
                    currentAttendeeCount = event.currentAttendeeCount,
                    isRegistered = registeredEventIds.contains(event.eventId),
                )
            }

            val regEventsNotInMapped = activeRegistrations
                .filter { reg -> mappedEvents.none { it.eventId == reg.eventId } }
                .map { reg ->
                    val statusEnum = EventStatusBadgeStyler.fromDates(reg.eventStartAt, reg.eventEndAt, now)
                    DashboardUpcomingEvent(
                        eventId = reg.eventId,
                        registrationId = reg.registrationId,
                        title = reg.eventTitle?.takeIf { it.isNotBlank() } ?: strings.get(R.string.dashboard_registered_event),
                        location = reg.eventLocation,
                        category = null,
                        eventStartAt = reg.eventStartAt,
                        status = EventStatusBadgeStyler.displayLabel(statusEnum),
                        description = null,
                        eventEndAt = reg.eventEndAt,
                        capacity = 0,
                        currentAttendeeCount = 0,
                        isRegistered = true,
                        capacityUnknown = true,
                    )
                }

            val summaryUpcoming = if (summaryResult is NetworkResult.Success) {
                summaryResult.data.upcomingEvents.orEmpty()
            } else {
                emptyList()
            }
            val summaryEventsNotInMapped = summaryUpcoming
                .filter { sumEvt ->
                    mappedEvents.none { it.eventId == sumEvt.eventId } &&
                        regEventsNotInMapped.none { it.eventId == sumEvt.eventId }
                }

            val allEvents = mappedEvents + regEventsNotInMapped + summaryEventsNotInMapped

            fun isEnded(event: DashboardUpcomingEvent): Boolean {
                if (event.eventEndAt != null && event.eventEndAt.isBefore(now)) return true
                if (event.status.equals("Completed", ignoreCase = true) ||
                    event.status.equals("Ended", ignoreCase = true)
                ) return true
                return false
            }

            fun isActive(event: DashboardUpcomingEvent): Boolean {
                if (isEnded(event)) return false
                if (event.status.equals("Active", ignoreCase = true) ||
                    event.status.equals("Ongoing", ignoreCase = true) ||
                    event.status.equals("In Progress", ignoreCase = true)
                ) return true
                val start = event.eventStartAt
                return start != null && !start.isAfter(now)
            }

            val activeOrUpcomingComparator = Comparator<DashboardUpcomingEvent> { a, b ->
                val aActive = isActive(a)
                val bActive = isActive(b)
                if (aActive && !bActive) return@Comparator -1
                if (!aActive && bActive) return@Comparator 1
                val aStart = a.eventStartAt ?: Instant.MAX
                val bStart = b.eventStartAt ?: Instant.MAX
                aStart.compareTo(bStart)
            }

            // Next Event: only the attendee's own next registered event (ongoing first, then soonest).
            // Never fall back to a public or past event.
            val nextEvent = allEvents
                .filter { it.isRegistered && !isEnded(it) }
                .sortedWith(activeOrUpcomingComparator)
                .firstOrNull()

            val nextEventList = if (nextEvent != null) listOf(nextEvent) else emptyList()

            // Discover Events selection:
            // All candidates excluding nextEvent
            val remainingCandidates = allEvents.filter { it.eventId != nextEvent?.eventId }
            val activeOrUpcomingCandidates = remainingCandidates
                .filter { !isEnded(it) }
                .sortedWith(activeOrUpcomingComparator)

            // Never show past events here; an empty list renders the "No upcoming events" state.
            val discoverEvents = activeOrUpcomingCandidates.take(5)

            // Same definitions as the Registered tab chips: Registered = live registrations whose event
            // has not ended; Completed = live registrations whose event has ended.
            val tabCounts = DashboardStats.registrationCounts(registrations, now)
            // When /registrations failed, derive from the summary: counted registrations minus completed ones
            // (so ended events are not counted as Registered). Without a completed count the value is unknown.
            val summaryCompleted: Long? = (summaryResult as? NetworkResult.Success)?.data?.completedEventsCount
            val summaryTotalRegs: Long? = (summaryResult as? NetworkResult.Success)?.data?.totalRegistrations
            val registeredCount: Long? = if (registrationsResult is NetworkResult.Success) {
                tabCounts.registered.toLong()
            } else if (summaryTotalRegs != null && summaryCompleted != null) {
                (summaryTotalRegs - summaryCompleted).coerceAtLeast(0L)
            } else {
                null
            }

            val upcomingCount = if (eventsResult is NetworkResult.Success || allEvents.isNotEmpty()) {
                DashboardStats.upcomingCount(allEvents.map { it.eventStartAt }, now)
            } else if (summaryResult is NetworkResult.Success) {
                summaryResult.data.totalEvents.toInt()
            } else {
                0
            }

            val completedCount: Long? = if (registrationsResult is NetworkResult.Success) {
                tabCounts.completed.toLong()
            } else {
                summaryCompleted
            }

            if (summaryResult is NetworkResult.Success) {
                val summary = summaryResult.data.copy(
                    totalRegistrations = registeredCount,
                    totalEvents = upcomingCount.toLong(),
                    completedEventsCount = completedCount,
                    upcomingEvents = nextEventList,
                    discoverEvents = discoverEvents,
                )
                view?.showSummary(summary)
            } else if (allEvents.isNotEmpty() || registrations.isNotEmpty()) {
                val fallbackSummary = DashboardSummary(
                    totalEvents = upcomingCount.toLong(),
                    totalRegistrations = registeredCount,
                    totalTransactions = 0L,
                    totalPoints = 0L,
                    completedEventsCount = completedCount,
                    totalNotifications = 0L,
                    fullName = sessionManager.getFullName(),
                    upcomingEvents = nextEventList,
                    discoverEvents = discoverEvents,
                )
                view?.showSummary(fallbackSummary)
                if (summaryResult is NetworkResult.Error) {
                    view?.showMessage(strings.get(R.string.dashboard_unable_to_load_latest_stats, summaryResult.message))
                }
            } else if (summaryResult is NetworkResult.Error) {
                view?.showError(summaryResult.message)
            }

            if (currentUserResult is NetworkResult.Error) {
                view?.showMessage(strings.get(R.string.dashboard_unable_to_refresh_account_role, currentUserResult.message))
            }
            if (eventsResult is NetworkResult.Error) {
                view?.showMessage(strings.get(R.string.dashboard_unable_to_load_events, eventsResult.message))
            }
            if (registrationsResult is NetworkResult.Error) {
                view?.showMessage(strings.get(R.string.dashboard_unable_to_load_registrations, registrationsResult.message))
            }
        }
    }

    fun openSection(title: String, message: String) {
        view?.openSection(title, message)
    }

    fun logout() {
        RegistrationsCache.clear()
        sessionManager.clearSession()
    }

    private fun computeEventStatus(event: AttendeeEventResponse, now: Instant): String {
        val status = EventStatusBadgeStyler.resolve(event.status, event.eventStartAt, event.eventEndAt, now)
        return EventStatusBadgeStyler.displayLabel(status)
    }
}

data class RegistrationCounts(val registered: Int, val completed: Int)

/** Pure dashboard tile math, kept free of Android types so it can be unit tested. */
object DashboardStats {
    fun isLive(status: RegistrationStatus): Boolean =
        status != RegistrationStatus.CANCELLED && status != RegistrationStatus.NO_SHOW

    fun registrationCounts(
        registrations: List<com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse>,
        now: Instant,
    ): RegistrationCounts {
        val live = registrations.filter { isLive(it.status) }
        val completed = live.count { it.eventEndAt?.isBefore(now) == true }
        return RegistrationCounts(registered = live.size - completed, completed = completed)
    }

    /** Events that have not started yet (start strictly after now); ongoing events are excluded. */
    fun upcomingCount(starts: List<Instant?>, now: Instant): Int = starts.count { it?.isAfter(now) == true }
}
