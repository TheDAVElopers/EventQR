package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RewardClaimFilterTest {

    private val eventId = UUID.randomUUID()
    private val attendeeId = UUID.randomUUID()

    private fun reward(allowDuplicates: Boolean) = RewardResponse(
        rewardId = UUID.randomUUID(),
        eventId = eventId,
        name = "Shirt",
        pointsRequired = 50,
        status = RewardStatus.ACTIVE,
        allowDuplicateClaims = allowDuplicates,
    )

    private fun redemption(rewardId: UUID, status: RedemptionStatus) = RewardRedemptionResponse(
        redemptionId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = attendeeId,
        rewardId = rewardId,
        pointsSpent = 50,
        status = status,
    )

    @Test
    fun redeemedReward_isDroppedWhenDuplicatesAreDisallowed() {
        val claimed = reward(allowDuplicates = false)
        val other = reward(allowDuplicates = false)

        val visible = claimableRewards(
            listOf(claimed, other),
            redeemedRewardIds(listOf(redemption(claimed.rewardId, RedemptionStatus.REDEEMED))),
        )

        assertEquals(listOf(other.rewardId), visible.map { it.rewardId })
    }

    @Test
    fun redeemedReward_staysClaimableWhenDuplicatesAreAllowed() {
        val claimed = reward(allowDuplicates = true)

        val visible = claimableRewards(
            listOf(claimed),
            redeemedRewardIds(listOf(redemption(claimed.rewardId, RedemptionStatus.REDEEMED))),
        )

        assertEquals(listOf(claimed.rewardId), visible.map { it.rewardId })
    }

    @Test
    fun rejectedOrPendingClaim_doesNotHideTheReward() {
        val reward = reward(allowDuplicates = false)

        val visible = claimableRewards(
            listOf(reward),
            redeemedRewardIds(
                listOf(
                    redemption(reward.rewardId, RedemptionStatus.REJECTED),
                    redemption(reward.rewardId, RedemptionStatus.PENDING),
                ),
            ),
        )

        assertEquals(listOf(reward.rewardId), visible.map { it.rewardId })
    }

    @Test
    fun claimedIds_onlyContainRedeemedRewards() {
        val redeemed = reward(allowDuplicates = false)
        val rejected = reward(allowDuplicates = false)

        val ids = redeemedRewardIds(
            listOf(
                redemption(redeemed.rewardId, RedemptionStatus.REDEEMED),
                redemption(rejected.rewardId, RedemptionStatus.REJECTED),
            ),
        )

        assertTrue(redeemed.rewardId.toString() in ids)
        assertTrue(rejected.rewardId.toString() !in ids)
    }
}
