package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse

interface ClaimedRewardsContract {
    interface View : AttendeeView {
        fun renderRedemptions(items: List<RewardRedemptionResponse>)
    }
}
