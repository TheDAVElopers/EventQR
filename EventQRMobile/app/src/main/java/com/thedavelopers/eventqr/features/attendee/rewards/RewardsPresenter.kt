package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
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
    private var loadJob: Job? = null
    /** Separate from [loadJob]: a refresh must never cancel an in-flight redeem POST. */
    private var redeemJob: Job? = null

    fun detach() {
        loadJob?.cancel()
        redeemJob?.cancel()
        scope.cancel()
        view = null
    }

    fun load(eventId: String, attendeeUserId: String?, includeUnavailable: Boolean = false) {
        view?.showLoading(true)
        loadJob?.cancel()
        loadJob = scope.launch {
            // The backend's claimable list ignores the caller, so the attendee's own claims must
            // narrow it here; otherwise an already-claimed reward still reads "Available".
            val redemptions = when (val result = repository.getMyRewardRedemptions(eventId)) {
                is NetworkResult.Success -> result.data
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.onClaimStateCheckFailed()
                    view?.showError(result.message)
                    return@launch
                }
                NetworkResult.Loading -> {
                    // Never leave the skeleton up on a read that will not finish.
                    view?.showLoading(false)
                    view?.onClaimStateCheckFailed()
                    view?.showError(strings.get(R.string.ui_attendee_load_error_body))
                    return@launch
                }
            }
            val rewards = when (val result = repository.getRewardsByEvent(eventId, includeUnavailable)) {
                is NetworkResult.Success -> result.data
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.onClaimStateCheckFailed()
                    view?.showError(result.message)
                    return@launch
                }
                NetworkResult.Loading -> {
                    view?.showLoading(false)
                    view?.onClaimStateCheckFailed()
                    view?.showError(strings.get(R.string.ui_attendee_load_error_body))
                    return@launch
                }
            }
            val claimedRewardIds = redeemedRewardIds(redemptions)
            // Render before the balance lands: the claim state must not depend on that call.
            view?.renderRewards(claimableRewards(rewards, claimedRewardIds), claimedRewardIds)

            val balanceResult = attendeeUserId?.takeIf { it.isNotBlank() }?.let { repository.getRewardBalance(eventId, it) }
            when (balanceResult) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
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
    }

    fun redeem(eventId: String, attendeeUserId: String?, rewardId: String) {
        val userId = attendeeUserId.orEmpty()
        if (userId.isBlank()) {
            view?.showMessage(strings.get(R.string.rewards_attendee_user_id_required))
            return
        }
        view?.showLoading(true)
        redeemJob = scope.launch {
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

/** Reward ids this attendee has REDEEMED; PENDING/REJECTED claims never block a duplicate check. */
internal fun redeemedRewardIds(redemptions: List<RewardRedemptionResponse>): Set<String> =
    redemptions.filter { it.status == RedemptionStatus.REDEEMED }
        .map { it.rewardId.toString() }
        .toSet()

/**
 * The rewards the attendee may still claim: a reward already redeemed by them is dropped unless the
 * organizer allows duplicate claims. Mirrors the backend's redeem-time duplicate check.
 */
internal fun claimableRewards(
    rewards: List<RewardResponse>,
    redeemedIds: Set<String>,
): List<RewardResponse> = rewards.filter { it.allowDuplicateClaims || it.rewardId.toString() !in redeemedIds }

/**
 * Index of the event to open in the rewards selector, or null when [requestedEventId] was supplied
 * by the caller and the attendee has no live registration for it. Null means "show the
 * not-registered state"; falling back to another event would show the wrong rewards and balance.
 * The first option is used only when no explicit id was supplied.
 */
internal fun resolveRewardsEventIndex(
    eventIds: List<String>,
    preferredEventId: String?,
    requestedEventId: String?,
): Int? {
    val preferredIndex = eventIds.indexOf(preferredEventId)
    if (preferredIndex >= 0) return preferredIndex
    if (!requestedEventId.isNullOrBlank()) return null
    return eventIds.indices.firstOrNull()
}
