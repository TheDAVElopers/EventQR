package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class RewardsPresenter(
    private var view: RewardsContract.View?,
    private val repository: AttendeeRepository,
    private val strings: UiStrings,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun load(eventId: String, attendeeUserId: String?) {
        view?.showLoading(true)
        job = scope.launch {
            when (val rewardsResult = repository.getRewardsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    val balanceResult = attendeeUserId?.takeIf { it.isNotBlank() }?.let { repository.getRewardBalance(eventId, it) }
                    when (balanceResult) {
                        is NetworkResult.Success -> {
                            view?.showLoading(false)
                            view?.renderRewards(rewardsResult.data)
                            view?.showBalance(balanceResult.data)
                        }

                        is NetworkResult.Error -> {
                            view?.showLoading(false)
                            view?.showError(balanceResult.message)
                        }

                        NetworkResult.Loading, null -> {
                            view?.showLoading(false)
                            view?.showError(strings.get(R.string.rewards_unable_to_load_reward_balance))
                        }
                    }
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showError(rewardsResult.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun redeem(eventId: String, attendeeUserId: String?, rewardId: String) {
        val userId = attendeeUserId.orEmpty()
        if (userId.isBlank()) {
            view?.showMessage(strings.get(R.string.rewards_attendee_user_id_required))
            return
        }
        view?.showLoading(true)
        job = scope.launch {
            when (val result = repository.redeemReward(
                RewardRedemptionRequest(
                    eventId = UUID.fromString(eventId),
                    attendeeUserId = UUID.fromString(userId),
                    rewardId = UUID.fromString(rewardId),
                )
            )) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showMessage(result.message ?: strings.get(R.string.rewards_reward_redeemed))
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
