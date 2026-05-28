package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse

interface RewardsContract {
    interface View : AttendeeView {
        fun showBalance(balance: PointBalanceResponse)
        fun renderRewards(items: List<RewardResponse>)
    }
}
