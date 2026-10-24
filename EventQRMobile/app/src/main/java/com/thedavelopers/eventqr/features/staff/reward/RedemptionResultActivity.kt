package com.thedavelopers.eventqr.features.staff.reward

import com.thedavelopers.eventqr.ui.components.EventQrDetailHeader
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
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.rewards.AppRewardExtras
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong

class RedemptionResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val approved = intent.getBooleanExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
        val message = intent.getStringExtra(StaffScreenExtras.EXTRA_REASON).orEmpty()
            .let { raw ->
                if (raw.trim().equals("DUPLICATE_CLAIM", ignoreCase = true)) getString(R.string.redemption_result_duplicate_claim) else raw
            }
            .ifBlank { getString(if (approved) R.string.redemption_result_reward_redeemed_successfully else R.string.redemption_result_reward_redemption_rejected) }
        val points = intent.getIntExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, 0)
        val remaining = intent.getIntExtra(AppRewardExtras.EXTRA_POINTS_BALANCE, 0)
        val rewardName = intent.getStringExtra(AppRewardExtras.EXTRA_REWARD_NAME).orEmpty()

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_reward_result_screen)

            // Top header bar (mirrors activity_edit_profile.xml lines 9-38)
            addView(buildHeaderBar("Reward Redemption"))

            val scroll = ScrollView(this@RedemptionResultActivity).apply {
                isFillViewport = true
            }
            scroll.addView(LinearLayout(this@RedemptionResultActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_28), dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_20))

                addView(spacer(dimenOffset(R.dimen.space_20)))

                // Status hero card
                addView(LinearLayout(this@RedemptionResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(
                        if (approved) R.drawable.bg_reward_success_hero else R.drawable.bg_reward_reject_hero
                    )
                    setPadding(dimenOffset(R.dimen.space_xl), dimenOffset(R.dimen.space_xxl), dimenOffset(R.dimen.space_xl), dimenOffset(R.dimen.space_xxl))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )

                    addView(LinearLayout(this@RedemptionResultActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.bg_reward_icon_circle)
                        layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.size_76), dimenOffset(R.dimen.size_76))

                        addView(TextView(this@RedemptionResultActivity).apply {
                            text = if (approved) "✓" else "✕"
                            setTextSizeRes(R.dimen.type_display)
                            gravity = Gravity.CENTER
                            setTextColor(if (approved) color(R.color.on_success_container) else color(R.color.on_error_container))
                            strong()
                        })
                    })

                    addView(spacer(dimenOffset(R.dimen.space_18)))

                    addView(TextView(this@RedemptionResultActivity).apply {
                        text = getString(if (approved) R.string.redemption_result_redemption_approved else R.string.redemption_result_redemption_rejected)
                        setTextSizeRes(R.dimen.type_title_large)
                        gravity = Gravity.CENTER
                        setTextColor(color(R.color.on_primary))
                        strong()
                    })

                    addView(spacer(dimenOffset(R.dimen.space_sm)))

                    addView(TextView(this@RedemptionResultActivity).apply {
                        text = message
                        setTextSizeRes(R.dimen.type_body)
                        gravity = Gravity.CENTER
                        setTextColor(color(R.color.on_dark_80))
                    })
                })

                addView(spacer(dimenOffset(R.dimen.space_18)))

                // Detail panel
                val detailCard = LinearLayout(this@RedemptionResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_reward_dark_card)
                    setPadding(0, dimenOffset(R.dimen.space_6), 0, dimenOffset(R.dimen.space_6))
                }
                addView(detailCard)

                if (rewardName.isNotBlank() && approved) {
                    detailCard.addView(detailRow("Reward", rewardName, iconColor = color(R.color.secondary_light)))
                    detailCard.addView(detailDivider())
                }
                detailCard.addView(detailRow(
                    "Points deducted",
                    if (approved) "−$points pts" else "0 pts",
                    iconColor = if (approved) color(R.color.danger) else color(R.color.text_secondary),
                ))
                detailCard.addView(detailDivider())
                val remainingColor = if (remaining < points) color(R.color.warning_dark) else color(R.color.success_dark)
                detailCard.addView(detailRow(
                    "Remaining balance",
                    "$remaining pts",
                    iconColor = remainingColor,
                    valueAccent = remainingColor,
                ))

                if (!approved) {
                    detailCard.addView(detailDivider())
                    detailCard.addView(detailRow(
                        "Outcome",
                        "No points were deducted",
                        iconColor = color(R.color.text_secondary),
                        valueAccent = color(R.color.text_body),
                    ))
                }

                addView(spacer(dimenOffset(R.dimen.size_26)))

                // CTA button
                addView(TextView(this@RedemptionResultActivity).apply {
                    text = "Tap to continue scanning"
                    setTextSizeRes(R.dimen.type_body_large)
                    gravity = Gravity.CENTER
                    setTextColor(color(R.color.on_primary))
                    strong()
                    setBackgroundResource(R.drawable.bg_reward_cta)
                    setPadding(0, dimenOffset(R.dimen.space_lg), 0, dimenOffset(R.dimen.space_lg))
                    setElevation(dimenOffset(R.dimen.elevation_lifted).toFloat())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    setOnClickListener { finish() }
                })
            })
            addView(scroll)
        })
    }

    private fun buildHeaderBar(title: String): View = EventQrDetailHeader(this).apply { this.title = title }

    private fun selectableItemBackgroundBorderless(): Int {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        return outValue.resourceId
    }

    private fun detailRow(label: String, value: String, iconColor: Int, valueAccent: Int? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_14), dimenOffset(R.dimen.space_20), dimenOffset(R.dimen.space_14))

            addView(TextView(this@RedemptionResultActivity).apply {
                text = "•"
                setTextSizeRes(R.dimen.type_title)
                setTextColor(iconColor)
                strong()
                layoutParams = LinearLayout.LayoutParams(dimenOffset(R.dimen.space_28), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            addView(TextView(this@RedemptionResultActivity).apply {
                text = label
                setTextSizeRes(R.dimen.type_body)
                setTextColor(color(R.color.text_body))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(this@RedemptionResultActivity).apply {
                text = value
                setTextSizeRes(R.dimen.type_body)
                gravity = Gravity.END
                setTextColor(valueAccent ?: color(R.color.text_primary))
                strong()
            })
        }

    private fun detailDivider(): View =
        View(this).apply {
            setBackgroundColor(color(R.color.outline))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dimenOffset(R.dimen.stroke_hairline),
            ).also { it.leftMargin = dimenOffset(R.dimen.space_xxxl); it.rightMargin = dimenOffset(R.dimen.space_20) }
        }

    private fun spacer(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        }

}
