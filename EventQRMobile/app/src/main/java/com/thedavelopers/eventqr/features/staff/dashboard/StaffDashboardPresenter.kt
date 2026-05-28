package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StaffDashboardPresenter(
    private var view: StaffDashboardContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadData() {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getEvents()) {
                is NetworkResult.Success -> {
                    view?.renderEvents(result.data)
                    if (result.data.isEmpty()) {
                        view?.renderRecentScans(emptyList())
                        view?.updateStats(0, 0)
                    } else {
                        val recentTransactions = mutableListOf<TransactionResponse>()
                        for (event in result.data) {
                            when (val trans = repository.getTransactionsByEvent(event.eventId.toString())) {
                                is NetworkResult.Success -> recentTransactions.addAll(trans.data)
                                is NetworkResult.Error -> Unit
                                NetworkResult.Loading -> Unit
                            }
                        }
                        val sortedTransactions = recentTransactions.sortedByDescending { it.scannedAt ?: java.time.Instant.EPOCH }
                        view?.renderRecentScans(sortedTransactions.take(5))
                        view?.updateStats(sortedTransactions.size, sortedTransactions.count { it.transactionType.name == "ENTRY" || it.transactionType.name == "ATTENDANCE" })
                    }
                }
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}
