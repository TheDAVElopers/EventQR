package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClaimedRewardsPresenter(
    private var view: ClaimedRewardsContract.View?,
    private val repository: AttendeeRepository,
) {
    private val scope = kotlinx.coroutines.MainScope()
    private var job: Job? = null

    fun detach() {
        job?.cancel()
        scope.cancel()
        view = null
    }

    fun loadRedemptions(eventId: String) {
        job?.cancel()
        view?.showLoading(true)
        job = scope.launch {
            when (val redemptionsResult = repository.getMyRewardRedemptions(eventId)) {
                is NetworkResult.Success -> {
                    val rewardNames = when (val rewardsResult = repository.getRewardsByEvent(eventId, includeUnavailable = true)) {
                        is NetworkResult.Success -> rewardsResult.data.associate { it.rewardId.toString() to it.name }
                        else -> emptyMap()
                    }

                    val eventTitle = when (val eventResult = repository.getEvent(eventId)) {
                        is NetworkResult.Success -> eventResult.data.title
                        else -> null
                    }

                    view?.showLoading(false)
                    view?.renderRedemptions(redemptionsResult.data, eventTitle, rewardNames)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showError(redemptionsResult.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /** Loads claims across several events (the "My Claims" view); shows an error when no event succeeded. */
    fun loadAllRedemptions(eventIds: List<String>, eventTitlesById: Map<String, String>) {
        job?.cancel()
        view?.showLoading(true)
        job = scope.launch {
            val perEvent = coroutineScope {
                eventIds.map { eventId ->
                    async {
                        when (val result = repository.getMyRewardRedemptions(eventId)) {
                            is NetworkResult.Success -> {
                                val names = if (result.data.isNotEmpty()) {
                                    (repository.getRewardsByEvent(eventId, includeUnavailable = true) as? NetworkResult.Success)?.data
                                        ?.associate { it.rewardId.toString() to it.name }
                                        .orEmpty()
                                } else {
                                    emptyMap()
                                }
                                EventClaims(result.data, names, null)
                            }
                            is NetworkResult.Error -> EventClaims(null, emptyMap(), result.message)
                            NetworkResult.Loading -> EventClaims(null, emptyMap(), null)
                        }
                    }
                }.awaitAll()
            }
            val all = perEvent.flatMap { it.redemptions.orEmpty() }
            val rewardNames = perEvent.fold(mutableMapOf<String, String>()) { acc, e -> acc.apply { putAll(e.names) } }
            val firstError = perEvent.firstNotNullOfOrNull { it.error }
            val succeeded = perEvent.count { it.redemptions != null }
            view?.showLoading(false)
            if (succeeded == 0 && eventIds.isNotEmpty()) {
                // Nothing succeeded (all failed or never finished): an error, never a misleading empty list.
                view?.showError(firstError.orEmpty())
            } else {
                view?.renderRedemptions(all, null, rewardNames, eventTitlesById)
            }
        }
    }

    private class EventClaims(
        val redemptions: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse>?,
        val names: Map<String, String>,
        val error: String?,
    )
}
