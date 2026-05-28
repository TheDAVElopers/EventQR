package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class RewardsPresenter(
    private var view: RewardsContract.View?,
    private val repository: AttendeeRepository,
) {
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        view = null
    }

    fun load(eventId: String, attendeeUserId: String?) {
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val rewardsResult = repository.getRewardsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    val balanceResult = attendeeUserId?.takeIf { it.isNotBlank() }?.let { repository.getRewardBalance(eventId, it) }
                    view?.showLoading(false)
                    view?.renderRewards(rewardsResult.data)
                    if (balanceResult is NetworkResult.Success) {
                        view?.showBalance(balanceResult.data)
                    }
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showMessage(rewardsResult.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun redeem(eventId: String, attendeeUserId: String?, rewardId: String) {
        val userId = attendeeUserId.orEmpty()
        if (userId.isBlank()) {
            view?.showMessage("Attendee user ID is required to redeem rewards")
            return
        }
        view?.showLoading(true)
        job = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.redeemReward(
                RewardRedemptionRequest(
                    eventId = UUID.fromString(eventId),
                    attendeeUserId = UUID.fromString(userId),
                    rewardId = UUID.fromString(rewardId),
                )
            )) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: "Reward redeemed")
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
