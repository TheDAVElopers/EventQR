package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StaffTransactionsPresenter(
    private var view: StaffTransactionsContract.View?,
    private val repository: StaffRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String) {
        if (eventId.isBlank()) {
            view?.showMessage("Select an assigned event first")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> view?.renderTransactions(result.data)
                is NetworkResult.Error -> view?.showMessage(result.message)
                NetworkResult.Loading -> Unit
            }
            view?.showLoading(false)
        }
    }
}
