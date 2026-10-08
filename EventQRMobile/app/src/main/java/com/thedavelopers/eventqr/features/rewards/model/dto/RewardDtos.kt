package com.thedavelopers.eventqr.features.rewards.model.dto

import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import java.time.Instant
import java.util.UUID

data class PointBalanceResponse(
    val eventId: UUID,
    val attendeeUserId: UUID,
    val pointsBalance: Int,
)

data class RewardRedemptionRequest(
    val eventId: UUID,
    val attendeeUserId: UUID,
    val rewardId: UUID,
)

data class RewardRedemptionResponse(
    val redemptionId: UUID,
    val eventId: UUID,
    val attendeeUserId: UUID,
    val rewardId: UUID,
    val pointsSpent: Int,
    val status: RedemptionStatus,
    val redeemedAt: Instant? = null,
    val reason: String? = null,
)

data class RewardRequest(
    val eventId: UUID,
    val name: String,
    val pointsRequired: Int,
    val stockQuantity: Int? = null,
    val allowDuplicateClaims: Boolean = false,
    /** Optional; omitted (null) on update keeps the stored description. */
    val description: String? = null,
    /** Immutable total supply the organizer configured. Null with [unlimitedStock] true = unlimited. */
    val totalQuantity: Int? = null,
    /** True when the organizer left quantity blank (Gson drops nulls, so unlimited needs an explicit flag). */
    val unlimitedStock: Boolean? = null,
)

data class RewardResponse(
    val rewardId: UUID,
    val eventId: UUID,
    val name: String,
    val description: String? = null,
    val pointsRequired: Int,
    val status: RewardStatus,
    /** REMAINING stock (decremented on every redemption); null = unlimited. */
    val stockQuantity: Int? = null,
    val allowDuplicateClaims: Boolean = false,
    /** Number of redeemed claims. */
    val claimedCount: Long = 0L,
    /** Total supply configured by the organizer; null = unlimited. */
    val totalQuantity: Int? = null,
)
