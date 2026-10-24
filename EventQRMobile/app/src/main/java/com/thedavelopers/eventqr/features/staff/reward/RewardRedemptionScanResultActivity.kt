package com.thedavelopers.eventqr.features.staff.reward

import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse
import kotlinx.coroutines.launch
import java.util.UUID
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong

class RewardRedemptionScanResultActivity : AppCompatActivity() {

    private val repository by lazy { StaffRepository(this) }
    private var eventId: String = ""
    private var attendeeUserId: String = ""
    private var attendeeName: String = ""
    private var pointsBalance: Int = 0
    private var redemptionScanLogId: String = ""
    private var staffUserId: String = ""
    private var scanRejected: Boolean = false
    private var rejectionReason: String = ""
    private var rewardsContainer: LinearLayout? = null
    private var balanceText: TextView? = null
    private var emptyText: TextView? = null
    private var inFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        attendeeUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        attendeeName = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orEmpty()
        pointsBalance = intent.getIntExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, 0)
        redemptionScanLogId = intent.getStringExtra(AppRewardExtras.EXTRA_REDEMPTION_SCAN_LOG_ID).orEmpty()
        staffUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID).orEmpty()
        scanRejected = intent.getBooleanExtra(StaffScreenExtras.EXTRA_SCAN_REJECTED, false)
        rejectionReason = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_REJECTION_REASON).orEmpty()
        setContentView(buildUi())
        loadRewards()
    }

    private fun buildUi(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_reward_result_screen)

            // Top header bar (mirrors activity_edit_profile.xml lines 9-38)
            addView(buildHeaderBar("Reward Redemption"))

            val scroll = ScrollView(this@RewardRedemptionScanResultActivity).apply {
                isFillViewport = true
            }
            val content = LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_28), dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_20))
            }
            scroll.addView(content)

            content.addView(spacer(dimenOffset(R.dimen.space_20)))

            content.addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_reward_points)
                setPadding(dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_20))

                addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = attendeeName.ifBlank { "Attendee" }
                        .split(" ")
                        .take(2)
                        .joinToString("") { it.firstOrNull()?.toString().orEmpty() }
                        .ifBlank { "?" }
                        .uppercase()
                    setTextSizeRes(R.dimen.type_title_large)
                    gravity = Gravity.CENTER
                    setTextColor(color(R.color.text_primary))
                    strong()
                    setBackgroundResource(R.drawable.bg_reward_icon_circle)
                    layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.button_primary_height), dimenOffset(R.dimen.button_primary_height))
                })

                addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dimenOffset(R.dimen.space_lg), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = attendeeName.ifBlank { "Attendee" }
                        setTextSizeRes(R.dimen.type_subtitle)
                        setTextColor(color(R.color.on_primary))
                        strong()
                    })

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "Points Balance"
                        setTextSizeRes(R.dimen.type_body_compact)
                        setTextColor(color(R.color.secondary_container))
                        setPadding(0, dimenOffset(R.dimen.space_xxs), 0, 0)
                    })
                })

                addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END

                    balanceText = TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = pointsBalance.toString()
                        setTextSizeRes(R.dimen.type_amount_34)
                        setTextColor(color(R.color.on_primary))
                        strong()
                    }.also { addView(it) }

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "pts available"
                        setTextSizeRes(R.dimen.type_caption)
                        setTextColor(color(R.color.secondary_container))
                    })
                })
            })

            content.addView(spacer(dimenOffset(R.dimen.space_xl)))

            if (scanRejected) {
                content.addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_reward_dark_card)
                    setPadding(dimenOffset(R.dimen.space_xl), dimenOffset(R.dimen.space_28), dimenOffset(R.dimen.space_xl), dimenOffset(R.dimen.space_28))

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "✕"
                        setTextSizeRes(R.dimen.type_amount_32)
                        gravity = Gravity.CENTER
                        setTextColor(color(R.color.error))
                        strong()
                        setBackgroundResource(R.drawable.bg_reward_icon_circle)
                        layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.size_68), dimenOffset(R.dimen.size_68))
                    })

                    addView(spacer(dimenOffset(R.dimen.space_lg)))

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "SCAN REJECTED"
                        setTextSizeRes(R.dimen.type_title)
                        gravity = Gravity.CENTER
                        setTextColor(color(R.color.error))
                        strong()
                    })

                    addView(spacer(dimenOffset(R.dimen.space_10)))

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = rejectionReason.ifBlank { "This scan was rejected. Please try again." }
                        setTextSizeRes(R.dimen.type_body_medium)
                        gravity = Gravity.CENTER
                        setTextColor(color(R.color.text_body))
                    })
                })
                showMessage(rejectionReason.ifBlank { "Scan was rejected." })
            } else {
                content.addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = "Available rewards"
                    setTextSizeRes(R.dimen.type_body_medium)
                    setTextColor(color(R.color.text_secondary))
                    strong()
                    setPadding(dimenOffset(R.dimen.space_xs), 0, 0, dimenOffset(R.dimen.space_md))
                })

                rewardsContainer = LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                emptyText = TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = "No eligible rewards for this attendee."
                    setTextSizeRes(R.dimen.type_body_medium)
                    setTextColor(color(R.color.text_secondary))
                    gravity = Gravity.CENTER
                    setPadding(dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_xl), dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_xl))
                    setBackgroundResource(R.drawable.bg_reward_list_card)
                    visibility = View.GONE
                }
                content.addView(rewardsContainer)
                content.addView(emptyText)
            }

            addView(scroll)
        }
    }

    private fun loadRewards() {
        if (scanRejected) {
            return
        }
        if (eventId.isBlank()) {
            showMessage(getString(R.string.reward_redemption_scan_result_missing_event_context))
            return
        }
        lifecycleScope.launch {
            when (val result = repository.getRewardsByEvent(eventId)) {
                is NetworkResult.Success -> renderRewards(result.data)
                is NetworkResult.Error -> showMessage(result.message.ifBlank { "Unable to load rewards." })
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderRewards(rewards: List<RewardResponse>) {
        val list = rewards.filter {
            it.status == RewardStatus.ACTIVE
                    && it.pointsRequired <= pointsBalance
                    && (it.stockQuantity == null || it.stockQuantity > 0)
        }
        val container = rewardsContainer ?: return
        container.removeAllViews()
        if (list.isEmpty()) {
            emptyText?.visibility = View.VISIBLE
            return
        }
        emptyText?.visibility = View.GONE
        list.forEachIndexed { index, reward ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dimenOffset(R.dimen.space_18), dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_18), dimenOffset(R.dimen.space_lg))
                setBackgroundResource(R.drawable.bg_reward_dark_card)
                setElevation(dimenOffset(R.dimen.elevation_lifted).toFloat())
                setOnClickListener { confirmRedemption(reward) }
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this).apply {
                text = reward.name
                setTextSizeRes(R.dimen.type_subtitle)
                setTextColor(color(R.color.text_primary))
                strong()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            titleRow.addView(TextView(this).apply {
                text = "☆ ${reward.pointsRequired} pts"
                setTextSizeRes(R.dimen.type_body_compact)
                setTextColor(color(R.color.secondary_light))
                strong()
                setBackgroundResource(R.drawable.bg_purple_pill)
                setPadding(dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.space_5), dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.space_5))
            })
            card.addView(titleRow)

            val detailRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dimenOffset(R.dimen.space_14), 0, 0)
            }
            val stock = reward.stockQuantity?.let { "$it left" } ?: "Unlimited stock"
            detailRow.addView(TextView(this).apply {
                text = stock
                setTextSizeRes(R.dimen.type_body_compact)
                setTextColor(color(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val allowDuplicates = reward.allowDuplicateClaims
            detailRow.addView(TextView(this).apply {
                text = getString(if (allowDuplicates) R.string.reward_redemption_scan_result_duplicate_claims_allowed else R.string.reward_redemption_scan_result_one_claim_only)
                setTextSizeRes(R.dimen.type_caption)
                strong()
                setTextColor(if (allowDuplicates) color(R.color.warning_dark) else color(R.color.on_success_container))
                setBackgroundResource(if (allowDuplicates) R.drawable.bg_yellow_notice else R.drawable.bg_green_pill)
                setPadding(dimenOffset(R.dimen.space_10), dimenOffset(R.dimen.space_xs), dimenOffset(R.dimen.space_10), dimenOffset(R.dimen.space_xs))
            })
            card.addView(detailRow)

            container.addView(card)
            if (index < list.size - 1) {
                container.addView(spacer(dimenOffset(R.dimen.space_md)))
            }
        }
    }

    private fun confirmRedemption(reward: RewardResponse) {
        if (inFlight) return
        android.app.AlertDialog.Builder(this)
            .setTitle("Redeem ${reward.name}")
            .setMessage("This will deduct ${reward.pointsRequired} points from this attendee. Continue?")
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            .setPositiveButton(getString(R.string.reward_redemption_scan_result_redeem)) { _, _ -> redeem(reward) }
            .show()
    }

    private fun redeem(reward: RewardResponse) {
        if (inFlight) return
        if (attendeeUserId.isBlank() || redemptionScanLogId.isBlank()) {
            showMessage(getString(R.string.reward_redemption_scan_result_missing_redemption_context))
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
                    showMessage(result.message.ifBlank { "Failed to redeem reward." })
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

    private fun buildHeaderBar(title: String): View = EventQrDetailHeader(this).apply { this.title = title }

    private fun selectableItemBackgroundBorderless(): Int {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        return outValue.resourceId
    }

    private fun spacer(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        }

}
