package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

class TransactionHistoryPresenter(
    private var view: TransactionHistoryContract.View?,
    private val repository: AttendeeRepository,
) {
    private val scope = MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun load(eventId: String? = null) {
        view?.showLoading(true)
        job = scope.launch {
            val result = if (eventId.isNullOrBlank()) {
                repository.getMyTransactions()
            } else {
                repository.getMyEventTransactions(eventId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderTransactions(result.data.sortedByDescending { it.scannedAt ?: Instant.EPOCH })
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
