package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse

interface ClaimedRewardsContract {
    interface View : AttendeeView {
        fun showError(message: String)
        fun renderRedemptions(
            items: List<RewardRedemptionResponse>,
            eventTitle: String?,
            rewardNamesById: Map<String, String>,
            eventTitlesById: Map<String, String> = emptyMap(),
        )
    }
}
