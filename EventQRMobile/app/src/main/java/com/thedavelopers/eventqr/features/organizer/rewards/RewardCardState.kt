package com.thedavelopers.eventqr.features.organizer.rewards

import android.content.Context
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse

/**
 * Pure stock math for the organizer reward card. `stockQuantity` is the REMAINING stock and is never
 * used here; the ratio is always claimed vs the immutable total supply.
 */
data class RewardCardState(
    val claimedCount: Long,
    val totalQuantity: Int?,
) {
    val unlimited: Boolean get() = totalQuantity == null
    val outOfStock: Boolean get() = totalQuantity != null && claimedCount >= totalQuantity

    companion object {
        fun of(reward: RewardResponse) = RewardCardState(reward.claimedCount, reward.totalQuantity)
    }
}

/** Outcome of validating the quantity field of the reward dialog. */
sealed interface RewardQuantity {
    /** Blank input: unlimited supply. */
    data object Unlimited : RewardQuantity
    data class Limited(val total: Int) : RewardQuantity
    /** Edit only: the field was not touched, so stock is left exactly as it is on the server. */
    data object Unchanged : RewardQuantity
    data object Invalid : RewardQuantity
}

fun parseRewardQuantity(raw: String): RewardQuantity {
    val text = raw.trim()
    if (text.isEmpty()) return RewardQuantity.Unlimited
    val value = text.toIntOrNull() ?: return RewardQuantity.Invalid
    return if (value < 0) RewardQuantity.Invalid else RewardQuantity.Limited(value)
}

/** Parses the field; on edit, text equal to the prefilled [original] means "unchanged". */
fun resolveRewardQuantity(raw: String, original: String?): RewardQuantity =
    if (original != null && raw.trim() == original.trim()) RewardQuantity.Unchanged else parseRewardQuantity(raw)

/**
 * Description rules (backend: null/absent keeps, "" clears):
 * create -> blank omitted; edit -> cleared sends "", unchanged re-sends the existing text, otherwise the new text.
 */
fun resolveRewardDescription(input: String, existing: String?, isEdit: Boolean): String? {
    val trimmed = input.trim()
    return when {
        trimmed.isNotEmpty() -> trimmed
        isEdit && !existing.isNullOrBlank() -> ""
        else -> null
    }
}

/**
 * Builds the create/update body. Never sends remaining stock. Quantity: Limited -> totalQuantity only,
 * Unlimited -> unlimitedStock=true only, Unchanged/null -> neither (server leaves stock alone).
 */
fun buildRewardRequest(
    eventId: java.util.UUID,
    title: String,
    points: Int,
    quantity: RewardQuantity,
    description: String?,
    allowDuplicateClaims: Boolean,
): RewardRequest = RewardRequest(
    eventId = eventId,
    name = title,
    pointsRequired = points,
    allowDuplicateClaims = allowDuplicateClaims,
    description = description,
    totalQuantity = (quantity as? RewardQuantity.Limited)?.total,
    unlimitedStock = if (quantity is RewardQuantity.Unlimited) true else null,
)

/** "5/10 claimed" for a limited reward; "Unlimited" (no ratio) otherwise. Never uses the remaining stock. */
fun rewardClaimedText(context: Context, state: RewardCardState): String =
    if (state.totalQuantity == null) {
        context.getString(R.string.manage_rewards_unlimited)
    } else {
        context.getString(
            R.string.manage_rewards_claimed_ratio,
            String.format("%,d", state.claimedCount),
            String.format("%,d", state.totalQuantity),
        )
    }
