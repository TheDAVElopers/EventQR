package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ClaimedRewardsPresenter(
    private var view: ClaimedRewardsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun loadRedemptions(eventId: String) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getRewardRedemptions(eventId)) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.renderRedemptions(result.data)
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
