package com.thedavelopers.eventqr.features.staff.reward

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset

/**
 * Reward redemption result (§5.14/§6.3 anatomy, flat): a 160dp semantic block, a hairline spec
 * table, one primary action. Built in Kotlin, but every value comes from a token and every colour
 * from a ui_* role - no GradientDrawable, no hero drawable, no bullet glyphs.
 */
class RedemptionResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEventQrSystemBarAppearance(lightStatusBars = false)

        val approved = intent.getBooleanExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
        val stateLabel = getString(
            if (approved) R.string.ui_staff_redemption_approved else R.string.ui_staff_redemption_rejected,
        )
        val message = intent.getStringExtra(StaffScreenExtras.EXTRA_REASON).orEmpty()
            .let { raw ->
                if (raw.trim().equals("DUPLICATE_CLAIM", ignoreCase = true)) {
                    getString(R.string.redemption_result_duplicate_claim)
                } else {
                    raw
                }
            }
            .takeIf { it.isNotBlank() && !it.equals(stateLabel, ignoreCase = true) }
            ?: getString(
                if (approved) R.string.ui_staff_redemption_done_body
                else R.string.ui_staff_redemption_rejected_body,
            )
        val points = intent.getIntExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, 0)
        val remaining = intent.getIntExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, 0)
        val rewardName = intent.getStringExtra(AppRewardExtras.EXTRA_REWARD_NAME).orEmpty()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Full-bleed semantic block, matching the scan / transaction result screens.
        content.addView(resultBlock(approved, message))

        val padded = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dimenOffset(R.dimen.space_20),
                dimenOffset(R.dimen.space_24),
                dimenOffset(R.dimen.space_20),
                dimenOffset(R.dimen.space_24),
            )
        }

        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (rewardName.isNotBlank() && approved) {
            table.addView(detailRow(getString(R.string.ui_staff_reward), rewardName))
            table.addView(rowDivider())
        }
        table.addView(
            detailRow(
                getString(R.string.ui_staff_points_deducted),
                if (approved) getString(R.string.ui_attendee_points_spent, points) else getString(R.string.ui_attendee_points_cost, "0"),
            ),
        )
        table.addView(rowDivider())
        table.addView(
            detailRow(getString(R.string.ui_staff_remaining_balance), getString(R.string.ui_attendee_points_cost, remaining.toString())),
        )
        if (!approved) {
            table.addView(rowDivider())
            table.addView(detailRow(getString(R.string.ui_staff_outcome), getString(R.string.ui_staff_no_points_deducted)))
        }
        padded.addView(table)

        padded.addView(spacer(R.dimen.space_32))
        padded.addView(primaryButton(getString(R.string.ui_staff_continue_scanning)) { finish() })
        content.addView(padded)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content)
        scroll.applyEventQrBottomInsetPadding()

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(color(R.color.ui_background))
                addView(EventQrDetailHeader(this@RedemptionResultActivity).apply {
                    title = getString(R.string.ui_staff_reward_redemption)
                })
                addView(scroll)
            },
        )
    }

    private fun resultBlock(approved: Boolean, message: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(color(if (approved) R.color.ui_success else R.color.ui_error))
            minimumHeight = dimenOffset(R.dimen.ui_staff_result_block_height)
            setPadding(
                dimenOffset(R.dimen.ui_gutter),
                dimenOffset(R.dimen.space_20),
                dimenOffset(R.dimen.ui_gutter),
                dimenOffset(R.dimen.space_20),
            )
            addView(
                ImageView(this@RedemptionResultActivity).apply {
                    setImageResource(if (approved) AppIcons.Check else AppIcons.Close)
                    setColorFilter(color(R.color.ui_on_primary))
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LinearLayout.LayoutParams(
                        dimenOffset(R.dimen.icon_lg),
                        dimenOffset(R.dimen.icon_lg),
                    )
                },
            )
            addView(
                TextView(this@RedemptionResultActivity).apply {
                    text = getString(
                        if (approved) R.string.ui_staff_redemption_approved else R.string.ui_staff_redemption_rejected,
                    )
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_DisplayM)
                    setTextColor(color(R.color.ui_on_primary))
                    setPadding(0, dimenOffset(R.dimen.space_md), 0, 0)
                },
            )
            addView(
                TextView(this@RedemptionResultActivity).apply {
                    text = message
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
                    setTextColor(color(R.color.ui_on_primary))
                    setPadding(0, dimenOffset(R.dimen.space_4), 0, 0)
                },
            )
        }

    private fun detailRow(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dimenOffset(R.dimen.ui_metric_row_height)
            addView(
                TextView(this@RedemptionResultActivity).apply {
                    text = label
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_Overline)
                    setTextColor(color(R.color.ui_text_secondary))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(
                TextView(this@RedemptionResultActivity).apply {
                    text = value
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_Body)
                    setTextColor(color(R.color.ui_text_primary))
                    gravity = Gravity.END
                    setPadding(dimenOffset(R.dimen.space_md), 0, 0, 0)
                },
            )
        }

    private fun rowDivider(): View = View(this).apply {
        setBackgroundColor(color(R.color.ui_hairline))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dimenOffset(R.dimen.stroke_hairline),
        )
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextAppearance(R.style.TextAppearance_EventQR_Type_ButtonLabel)
            setTextColor(color(R.color.ui_on_primary))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_ui_button_primary)
            isClickable = true
            isFocusable = true
            minimumHeight = dimenOffset(R.dimen.ui_button_height)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dimenOffset(R.dimen.ui_button_height),
            )
            setOnClickListener { onClick() }
        }

    private fun spacer(dimenRes: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dimenOffset(dimenRes),
        )
    }
}
