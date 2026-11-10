package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse

interface RewardsContract {
    interface View : AttendeeView {
        fun showBalance(balance: PointBalanceResponse)
        fun showError(message: String)
        /** [items] are the rewards the attendee can still claim; [claimedRewardIds] are the ones already REDEEMED. */
        fun renderRewards(items: List<RewardResponse>, claimedRewardIds: Set<String>)
        /**
         * The reward/claim read failed, so the claim state is unknown. The claim state must stay
         * fail-closed; screens that can show an indeterminate badge render a terminal error instead.
         */
        fun onClaimStateCheckFailed() = Unit
    }
}
