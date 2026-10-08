package com.thedavelopers.eventqr.features.staff.reward

import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
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
                setPadding(dp(20), dp(28), dp(20), dp(20))
            }
            scroll.addView(content)

            content.addView(spacer(dp(20)))

            content.addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_reward_points)
                setPadding(dp(20), dp(20), dp(20), dp(20))

                addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = attendeeName.ifBlank { "Attendee" }
                        .split(" ")
                        .take(2)
                        .joinToString("") { it.firstOrNull()?.toString().orEmpty() }
                        .ifBlank { "?" }
                        .uppercase()
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF151A2D.toInt())
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_reward_icon_circle)
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                })

                addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = attendeeName.ifBlank { "Attendee" }
                        textSize = 17f
                        setTextColor(0xFFFFFFFF.toInt())
                        setTypeface(typeface, Typeface.BOLD)
                    })

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "Points Balance"
                        textSize = 13f
                        setTextColor(0xFFEDE9FE.toInt())
                        setPadding(0, dp(2), 0, 0)
                    })
                })

                addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END

                    balanceText = TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = pointsBalance.toString()
                        textSize = 34f
                        setTextColor(0xFFFFFFFF.toInt())
                        setTypeface(typeface, Typeface.BOLD)
                    }.also { addView(it) }

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "pts available"
                        textSize = 12f
                        setTextColor(0xFFEDE9FE.toInt())
                    })
                })
            })

            content.addView(spacer(dp(24)))

            if (scanRejected) {
                content.addView(LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_reward_dark_card)
                    setPadding(dp(24), dp(28), dp(24), dp(28))

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "✕"
                        textSize = 32f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFDC2626.toInt())
                        setTypeface(typeface, Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_reward_icon_circle)
                        layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
                    })

                    addView(spacer(dp(16)))

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = "SCAN REJECTED"
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFDC2626.toInt())
                        setTypeface(typeface, Typeface.BOLD)
                    })

                    addView(spacer(dp(10)))

                    addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                        text = rejectionReason.ifBlank { "This scan was rejected. Please try again." }
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(0xFF374151.toInt())
                    })
                })
                showMessage(rejectionReason.ifBlank { "Scan was rejected." })
            } else {
                content.addView(TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = "Available rewards"
                    textSize = 14f
                    setTextColor(0xFF6B7280.toInt())
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(4), 0, 0, dp(12))
                })

                rewardsContainer = LinearLayout(this@RewardRedemptionScanResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                emptyText = TextView(this@RewardRedemptionScanResultActivity).apply {
                    text = "No eligible rewards for this attendee."
                    textSize = 14f
                    setTextColor(0xFF6B7280.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(24), dp(20), dp(24))
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
                setPadding(dp(18), dp(16), dp(18), dp(16))
                setBackgroundResource(R.drawable.bg_reward_dark_card)
                setElevation(dp(3).toFloat())
                setOnClickListener { confirmRedemption(reward) }
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this).apply {
                text = reward.name
                textSize = 17f
                setTextColor(0xFF151A2D.toInt())
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            titleRow.addView(TextView(this).apply {
                text = "☆ ${reward.pointsRequired} pts"
                textSize = 13f
                setTextColor(0xFF7C3AED.toInt())
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_purple_pill)
                setPadding(dp(12), dp(5), dp(12), dp(5))
            })
            card.addView(titleRow)

            val detailRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14), 0, 0)
            }
            val stock = reward.stockQuantity?.let { "$it left" } ?: "Unlimited stock"
            detailRow.addView(TextView(this).apply {
                text = stock
                textSize = 13f
                setTextColor(0xFF6B7280.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val allowDuplicates = reward.allowDuplicateClaims
            detailRow.addView(TextView(this).apply {
                text = getString(if (allowDuplicates) R.string.reward_redemption_scan_result_duplicate_claims_allowed else R.string.reward_redemption_scan_result_one_claim_only)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (allowDuplicates) 0xFFB45309.toInt() else Color.parseColor("#065F46"))
                setBackgroundResource(if (allowDuplicates) R.drawable.bg_yellow_notice else R.drawable.bg_green_pill)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
            card.addView(detailRow)

            container.addView(card)
            if (index < list.size - 1) {
                container.addView(spacer(dp(12)))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
