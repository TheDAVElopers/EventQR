package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class StaffDashboardPresenter(
    private var view: StaffDashboardContract.View?,
    private val repository: StaffRepository,
    private val strings: UiStrings? = null,
    private val scope: CoroutineScope = MainScope(),
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadData() {
        view?.showLoading(true)
        job = scope.launch {
            val (eventsResult, todayResult, summaryResult) = coroutineScope {
                val eventsDeferred = async { repository.getEvents() }
                val todayDeferred = async { repository.getMyTodayTransactions() }
                val summaryDeferred = async { repository.getMyTodaySummary() }
                Triple(eventsDeferred.await(), todayDeferred.await(), summaryDeferred.await())
            }

            when (eventsResult) {
                is NetworkResult.Error -> view?.showMessage(eventsResult.message)
                is NetworkResult.Success -> Unit
                NetworkResult.Loading -> Unit
            }

            when (todayResult) {
                is NetworkResult.Success -> view?.renderRecentScans(
                    todayResult.data.sortedByDescending { it.scannedAt ?: java.time.Instant.EPOCH }.take(5),
                )
                is NetworkResult.Error -> view?.renderRecentScans(emptyList())
                NetworkResult.Loading -> Unit
            }

            // Tile counts are server-side, scoped to the caller's own scans today (check-ins = distinct
            // attendees). Unknown is not zero: show "--" and say why.
            when (summaryResult) {
                is NetworkResult.Success ->
                    view?.updateStats(summaryResult.data.scannedToday.toInt(), summaryResult.data.successfulCheckIns.toInt())
                is NetworkResult.Error -> {
                    view?.updateStats(null, null)
                    view?.showMessage(strings?.get(R.string.staff_dashboard_stats_unavailable) ?: summaryResult.message)
                }
                NetworkResult.Loading -> Unit
            }

            when (val notifResult = repository.getMyNotifications()) {
                is NetworkResult.Success -> {
                    val unreadCount = notifResult.data.count { it.status != NotificationStatus.READ && it.readAt == null }
                    view?.showNotificationBadge(unreadCount)
                }
                is NetworkResult.Error -> view?.showNotificationBadge(0)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}
