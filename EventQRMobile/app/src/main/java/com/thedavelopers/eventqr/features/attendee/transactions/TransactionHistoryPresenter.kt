package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TransactionHistoryPresenter(
    private var view: TransactionHistoryContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String? = null) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            val result = if (eventId.isNullOrBlank()) {
                repository.getMyTransactions()
            } else {
                repository.getMyEventTransactions(eventId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderTransactions(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}
