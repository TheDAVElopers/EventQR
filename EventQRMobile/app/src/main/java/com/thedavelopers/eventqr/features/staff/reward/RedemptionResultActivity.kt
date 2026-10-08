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

class RedemptionResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val approved = intent.getBooleanExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
        val message = intent.getStringExtra(StaffScreenExtras.EXTRA_REASON).orEmpty()
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
                setPadding(dp(20), dp(28), dp(20), dp(20))

                addView(spacer(dp(20)))

                // Status hero card
                addView(LinearLayout(this@RedemptionResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(
                        if (approved) R.drawable.bg_reward_success_hero else R.drawable.bg_reward_reject_hero
                    )
                    setPadding(dp(24), dp(32), dp(24), dp(32))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )

                    addView(LinearLayout(this@RedemptionResultActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.bg_reward_icon_circle)
                        layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))

                        addView(TextView(this@RedemptionResultActivity).apply {
                            text = if (approved) "✓" else "✕"
                            textSize = 36f
                            gravity = Gravity.CENTER
                            setTextColor(if (approved) 0xFF047857.toInt() else 0xFF991B1B.toInt())
                            setTypeface(typeface, Typeface.BOLD)
                        })
                    })

                    addView(spacer(dp(18)))

                    addView(TextView(this@RedemptionResultActivity).apply {
                        text = getString(if (approved) R.string.redemption_result_redemption_approved else R.string.redemption_result_redemption_rejected)
                        textSize = 20f
                        gravity = Gravity.CENTER
                        setTextColor(0xFFFFFFFF.toInt())
                        setTypeface(typeface, Typeface.BOLD)
                    })

                    addView(spacer(dp(8)))

                    addView(TextView(this@RedemptionResultActivity).apply {
                        text = message
                        textSize = 15f
                        gravity = Gravity.CENTER
                        setTextColor(0xCCFFFFFF.toInt())
                    })
                })

                addView(spacer(dp(18)))

                // Detail panel
                val detailCard = LinearLayout(this@RedemptionResultActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_reward_dark_card)
                    setPadding(dp(0), dp(6), dp(0), dp(6))
                }
                addView(detailCard)

                if (rewardName.isNotBlank() && approved) {
                    detailCard.addView(detailRow("Reward", rewardName, iconColor = 0xFF7C3AED.toInt()))
                    detailCard.addView(detailDivider())
                }
                detailCard.addView(detailRow(
                    "Points deducted",
                    if (approved) "−$points pts" else "0 pts",
                    iconColor = if (approved) 0xFFEF4444.toInt() else 0xFF6B7280.toInt(),
                ))
                detailCard.addView(detailDivider())
                val remainingColor = if (remaining < points) 0xFFD97706.toInt() else 0xFF059669.toInt()
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
                        iconColor = 0xFF6B7280.toInt(),
                        valueAccent = 0xFF374151.toInt(),
                    ))
                }

                addView(spacer(dp(26)))

                // CTA button
                addView(TextView(this@RedemptionResultActivity).apply {
                    text = "Tap to continue scanning"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(0xFFFFFFFF.toInt())
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_reward_cta)
                    setPadding(dp(0), dp(16), dp(0), dp(16))
                    setElevation(dp(3).toFloat())
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
            setPadding(dp(20), dp(14), dp(20), dp(14))

            addView(TextView(this@RedemptionResultActivity).apply {
                text = "•"
                textSize = 18f
                setTextColor(iconColor)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            addView(TextView(this@RedemptionResultActivity).apply {
                text = label
                textSize = 15f
                setTextColor(0xFF4B5563.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(this@RedemptionResultActivity).apply {
                text = value
                textSize = 15f
                gravity = Gravity.END
                setTextColor(valueAccent ?: 0xFF111827.toInt())
                setTypeface(typeface, Typeface.BOLD)
            })
        }

    private fun detailDivider(): View =
        View(this).apply {
            setBackgroundColor(0xFFE5E7EB.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            ).also { it.leftMargin = dp(48); it.rightMargin = dp(20) }
        }

    private fun spacer(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
