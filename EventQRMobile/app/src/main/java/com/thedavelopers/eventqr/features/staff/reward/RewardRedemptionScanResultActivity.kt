package com.thedavelopers.eventqr.features.staff.reward

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Reward redemption scan result (§6.3, Gen C rewrite). The attendee and their balance are a flat
 * identity block; eligible rewards are rows inside ONE hairline card; a rejected scan is a
 * semantic block, not a centred dark card. All colours are ui_* roles, all sizes are dimens.
 */
class RewardRedemptionScanResultActivity : AppCompatActivity() {

    private val repository by lazy { StaffRepository(this) }
    private var eventId: String = ""
    private var attendeeUserId: String = ""
    private var attendeeName: String = ""
    private var pointsBalance: Int = 0
    private var eligibleRewardIds: Set<String>? = null
    private var redemptionScanLogId: String = ""
    private var staffUserId: String = ""
    private var scanRejected: Boolean = false
    private var rejectionReason: String = ""
    private var rewardsContainer: LinearLayout? = null
    private var rewardsEyebrow: TextView? = null
    private var emptyState: EventQrEmptyState? = null
    private var inFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        attendeeUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        attendeeName = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orEmpty()
        pointsBalance = intent.getIntExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, 0)
        eligibleRewardIds = intent.getStringArrayListExtra(AppRewardExtras.EXTRA_ELIGIBLE_REWARD_IDS)?.toSet()
        redemptionScanLogId = intent.getStringExtra(AppRewardExtras.EXTRA_REDEMPTION_SCAN_LOG_ID).orEmpty()
        staffUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID).orEmpty()
        scanRejected = intent.getBooleanExtra(StaffScreenExtras.EXTRA_SCAN_REJECTED, false)
        rejectionReason = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_REJECTION_REASON).orEmpty()
        setContentView(buildUi())
        loadRewards()
    }

    private fun buildUi(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dimenOffset(R.dimen.ui_gutter),
                dimenOffset(R.dimen.space_20),
                dimenOffset(R.dimen.ui_gutter),
                dimenOffset(R.dimen.space_24),
            )
        }

        content.addView(identityBlock())

        if (scanRejected) {
            content.addView(spacer(R.dimen.space_24))
            content.addView(rejectedBlock())
        } else {
            content.addView(spacer(R.dimen.space_32))
            rewardsEyebrow = eyebrow(getString(R.string.ui_staff_available_rewards)).also { content.addView(it) }
            content.addView(spacer(R.dimen.space_8))

            rewardsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_ui_card)
            }
            content.addView(rewardsContainer)

            emptyState = EventQrEmptyState(this).apply {
                show(
                    getString(R.string.ui_staff_no_eligible_rewards),
                    getString(R.string.ui_staff_no_eligible_rewards_body),
                )
                setIcon(AppIcons.Reward)
                visibility = View.GONE
            }
            content.addView(emptyState)
        }

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content)
        scroll.applyEventQrBottomInsetPadding()

        return LinearLayout(this).apply {            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.ui_background))
            addView(EventQrDetailHeader(this@RewardRedemptionScanResultActivity).apply {
                title = getString(R.string.ui_staff_reward_redemption)
            })
            addView(scroll)
        }
    }

    /** Flat identity strip: initial tile, name, balance - no dark "points card". */
    private fun identityBlock(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        setPadding(
            dimenOffset(R.dimen.ui_card_padding),
            dimenOffset(R.dimen.ui_card_padding),
            dimenOffset(R.dimen.ui_card_padding),
            dimenOffset(R.dimen.ui_card_padding),
        )

        addView(TextView(this@RewardRedemptionScanResultActivity).apply {
            text = initials(attendeeName)
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
            setTextColor(color(R.color.ui_primary))
            setBackgroundResource(R.drawable.bg_ui_icon_tile)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dimenOffset(R.dimen.ui_row_tile),
                dimenOffset(R.dimen.ui_row_tile),
            )
        })

        addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dimenOffset(R.dimen.space_lg), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                text = attendeeName.ifBlank { getString(R.string.ui_staff_result_attendee) }
                setTextAppearance(R.style.TextAppearance_EventQR_Type_TitleLarge)
                setTextColor(color(R.color.ui_text_primary))
            })
            addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                text = getString(R.string.ui_staff_points_balance)
                setTextAppearance(R.style.TextAppearance_EventQR_Type_Overline)
                setTextColor(color(R.color.ui_text_secondary))
                setPadding(0, dimenOffset(R.dimen.space_4), 0, 0)
            })
        })

        addView(TextView(this@RewardRedemptionScanResultActivity).apply {
            text = pointsBalance.toString()
            setTextAppearance(R.style.TextAppearance_EventQR_Type_NumericL)
            setTextColor(color(R.color.ui_text_primary))
            gravity = Gravity.END
        })
    }

    private fun rejectedBlock(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        setPadding(
            dimenOffset(R.dimen.ui_card_padding),
            dimenOffset(R.dimen.ui_card_padding),
            dimenOffset(R.dimen.ui_card_padding),
            dimenOffset(R.dimen.ui_card_padding),
        )

        addView(TextView(this@RewardRedemptionScanResultActivity).apply {
            text = getString(R.string.ui_staff_scan_rejected)
            setTextAppearance(R.style.TextAppearance_EventQR_Type_TitleLarge)
            setTextColor(color(R.color.ui_error))
        })
        addView(TextView(this@RewardRedemptionScanResultActivity).apply {
            text = rejectionReason.ifBlank { getString(R.string.ui_staff_scan_rejected_body) }
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Body)
            setTextColor(color(R.color.ui_text_secondary))
            setPadding(0, dimenOffset(R.dimen.space_4), 0, 0)
        })
    }

    private fun eyebrow(label: String): TextView = TextView(this).apply {
        text = label
        setTextAppearance(R.style.TextAppearance_EventQR_Type_Eyebrow)
        setTextColor(color(R.color.ui_text_secondary))
    }

    private fun loadRewards() {
        if (scanRejected) return
        if (eventId.isBlank()) {
            showMessage(getString(R.string.ui_staff_missing_event_context))
            return
        }
        lifecycleScope.launch {
            when (val result = repository.getRewardsByEvent(eventId)) {
                is NetworkResult.Success -> renderRewards(result.data)
                is NetworkResult.Error -> showMessage(result.message.ifBlank { getString(R.string.ui_staff_rewards_unavailable) })
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderRewards(rewards: List<RewardResponse>) {
        // The scan response already excludes rewards this attendee cannot claim (duplicate claims,
        // stock, affordability); intersect with it so the re-fetched list cannot reintroduce one.
        val eligibleIds = eligibleRewardIds
        val list = rewards.filter {
            (eligibleIds == null || it.rewardId.toString() in eligibleIds) &&
                it.status == RewardStatus.ACTIVE &&
                it.pointsRequired <= pointsBalance &&
                (it.stockQuantity == null || it.stockQuantity > 0)
        }
        val container = rewardsContainer ?: return
        container.removeAllViews()
        val hasRewards = list.isNotEmpty()
        rewardsEyebrow?.visibility = if (hasRewards) View.VISIBLE else View.GONE
        if (!hasRewards) {
            container.visibility = View.GONE
            emptyState?.visibility = View.VISIBLE
            return
        }
        container.visibility = View.VISIBLE
        emptyState?.visibility = View.GONE
        list.forEachIndexed { index, reward ->
            container.addView(rewardRow(reward))
            if (index < list.size - 1) {
                container.addView(rowDivider())
            }
        }
    }

    private fun rewardRow(reward: RewardResponse): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dimenOffset(R.dimen.ui_staff_reward_row)
            setPadding(
                dimenOffset(R.dimen.ui_card_padding),
                dimenOffset(R.dimen.space_md),
                dimenOffset(R.dimen.ui_card_padding),
                dimenOffset(R.dimen.space_md),
            )
            setBackgroundResource(R.drawable.bg_ui_button_icon)
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmRedemption(reward) }

            addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = reward.name
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
                    setTextColor(color(R.color.ui_text_primary))
                })
                addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                    val stock = reward.stockQuantity
                        ?.let { getString(R.string.ui_staff_stock_left, it) }
                        ?: getString(R.string.ui_staff_stock_unlimited)
                    val claim = getString(
                        if (reward.allowDuplicateClaims) R.string.ui_staff_duplicates_allowed else R.string.ui_staff_one_claim,
                    )
                    text = "$stock - $claim"
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
                    setTextColor(color(R.color.ui_text_secondary))
                    setPadding(0, dimenOffset(R.dimen.space_2), 0, 0)
                })
            })

            addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                text = getString(R.string.ui_attendee_points_cost, reward.pointsRequired.toString())
                setTextAppearance(R.style.TextAppearance_EventQR_Type_NumericS)
                setTextColor(color(R.color.ui_text_primary))
                gravity = Gravity.END
                setPadding(dimenOffset(R.dimen.space_md), 0, 0, 0)
            })
        }

    private fun confirmRedemption(reward: RewardResponse) {
        if (inFlight) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_staff_redeem_title, reward.name))
            .setMessage(getString(R.string.ui_staff_redeem_body, reward.pointsRequired))
            .setNegativeButton(getString(R.string.ui_staff_cancel), null)
            .setPositiveButton(getString(R.string.ui_staff_redeem)) { _, _ -> redeem(reward) }
            .show()
    }

    private fun redeem(reward: RewardResponse) {
        if (inFlight) return
        if (attendeeUserId.isBlank() || redemptionScanLogId.isBlank()) {
            showMessage(getString(R.string.ui_staff_missing_redemption_context))
            return
        }
        inFlight = true
        val request = RewardRedemptionGrantRequest(
            eventId = UUID.fromString(eventId),
            attendeeUserId = UUID.fromString(attendeeUserId),
            rewardId = reward.rewardId,
            staffUserId = staffUserId.takeIf { it.isNotBlank() }?.let(UUID::fromString),
            redemptionScanLogId = UUID.fromString(redemptionScanLogId),
        )
        lifecycleScope.launch {
            when (val result = repository.redeemRewardStaff(request)) {
                is NetworkResult.Success -> openResult(result.data)
                is NetworkResult.Error -> {
                    inFlight = false
                    showMessage(result.message.ifBlank { getString(R.string.ui_staff_redeem_failed) })
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun openResult(result: RewardRedemptionResultResponse) {
        startActivity(Intent(this, RedemptionResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, result.status?.name == "REDEEMED")
            putExtra(StaffScreenExtras.EXTRA_REASON, result.reason.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, result.pointsSpent)
            putExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, result.remainingBalance)
            putExtra(AppRewardExtras.EXTRA_REWARD_NAME, result.rewardName.orEmpty())
        })
        finish()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun initials(name: String): String = name.ifBlank { getString(R.string.ui_staff_result_attendee) }
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
        .uppercase(Locale.US)

    private fun rowDivider(): View = View(this).apply {
        setBackgroundColor(color(R.color.ui_hairline))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dimenOffset(R.dimen.stroke_hairline),
        )
    }

    private fun spacer(dimenRes: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dimenOffset(dimenRes),
        )
    }
}
