package com.thedavelopers.eventqr.features.organizer.rewards

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.ApiConfig
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.organizer.BG
import com.thedavelopers.eventqr.features.organizer.MUTED
import com.thedavelopers.eventqr.features.organizer.NAV_REWARDS
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.organizerEventDateLine
import com.thedavelopers.eventqr.features.organizer.PURPLE
import com.thedavelopers.eventqr.features.organizer.TEXT
import com.thedavelopers.eventqr.features.organizer.approvedOnly
import com.thedavelopers.eventqr.features.organizer.card
import com.thedavelopers.eventqr.features.organizer.centeredEmptyState
import com.thedavelopers.eventqr.features.organizer.dp
import com.thedavelopers.eventqr.features.organizer.errorState
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.organizerRefreshShell
import com.thedavelopers.eventqr.features.organizer.primaryButton
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.rounded
import com.thedavelopers.eventqr.features.organizer.saveSelectedEventId
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.text
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import java.util.UUID
import kotlinx.coroutines.launch

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

open class ManageRewardsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var content: LinearLayout
    private lateinit var rewardsEnabledSwitch: SwitchCompat
    private lateinit var rewardHost: LinearLayout
    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private val rewardsService by lazy { OrganizerRewardsApiProvider.get(this) }
    private var rewards: List<RewardResponse> = emptyList()
    private var redemptions: List<RewardRedemptionResponse> = emptyList()
    private var eventOptions: List<OrganizerMvpEvent> = emptyList()
    private var rewardsEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)

        val shell = organizerRefreshShell(
            title = "Rewards",
            selectedNav = NAV_REWARDS,
            showBack = false,
            topRightLabel = null,
            onTopRight = { showRewardDialog(null) },
            onRefresh = { loadRewards(showInitialLoading = false) },
        )
        content = shell.content
        refreshLayout = shell.swipeRefreshLayout

        lifecycleScope.launch {
            eventOptions = repository.getApprovedOrganizerEvents()
            val requestedEventId = intentEventId() ?: selectedEventId().takeIf { it.isNotBlank() }
            // Falls back to the saved, then the first, manageable event when nothing was requested.
            val resolvedEvent = resolveSelectedEvent(eventOptions, requestedEventId)

            val hasEvent = resolvedEvent != null
            refreshLayout.isEnabled = hasEvent

            if (resolvedEvent != null) {
                selectedEvent = resolvedEvent
                rewardsEnabled = selectedEvent.rewardsStatus.equals("Enabled", ignoreCase = true)
                buildScreen()
                loadRewards()
                refreshRewardsEnabledFromServer()
            } else {
                content.removeAllViews()
                content.addView(centeredEmptyState(
                    iconRes = R.drawable.ic_nav_gift,
                    title = "No Events Available",
                    subtext = "Create an event in the Events tab to manage reward redemptions.",
                ))
                content.setBackgroundColor(BG)
            }
        }
    }

    private fun buildScreen() {
        content.removeAllViews()
        content.setBackgroundColor(BG)

        // Card 1: SELECT EVENT
        content.addView(card(16).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_event_selector
            addView(text("SELECT EVENT", 11, true, Color.parseColor("#8E8EA9")).apply {
                letterSpacing = 0.05f
                setPadding(0, 0, 0, dp(8))
            })
            addView(customEventSelector(eventOptions, selectedEvent) { event ->
                if (event.id == selectedEvent.id) return@customEventSelector
                selectedEvent = event
                rewardsEnabled = event.rewardsStatus.equals("Enabled", ignoreCase = true)
                saveSelectedEventId(event.id)
                loadRewards()
                refreshRewardsEnabledFromServer()
            })
        })

        // Card 2: Event Rewards Toggle
        content.addView(card(16).apply {
            val row = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    marginEnd = dp(12)
                }
                background = rounded(Color.parseColor("#E6F4EA"), 12, null, density = resources.displayMetrics.density)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setImageResource(R.drawable.ic_nav_gift)
                setColorFilter(Color.parseColor("#059669"))
                contentDescription = null
            })

            row.addView(LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
                addView(text("Event Rewards", 16, true, TEXT).apply {
                    id = com.thedavelopers.eventqr.R.id.mrw_rewards_label
                })
                addView(text("Enable or disable reward redemption for this event.", 13, false, MUTED).apply {
                    id = com.thedavelopers.eventqr.R.id.mrw_rewards_desc
                    setPadding(0, dp(2), 0, 0)
                })
            })

            rewardsEnabledSwitch = SwitchCompat(this@ManageRewardsActivity).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_rewards_switch
                isChecked = rewardsEnabled
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                val thumbColors = intArrayOf(Color.WHITE, Color.WHITE)
                val trackColors = intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#D1D5DB"))
                androidx.core.graphics.drawable.DrawableCompat.setTintList(
                    thumbDrawable,
                    android.content.res.ColorStateList(states, thumbColors)
                )
                androidx.core.graphics.drawable.DrawableCompat.setTintList(
                    trackDrawable,
                    android.content.res.ColorStateList(states, trackColors)
                )
                setOnCheckedChangeListener { _, checked -> setRewardsEnabled(checked) }
            }
            row.addView(rewardsEnabledSwitch)
            addView(row)
        })

        // Section Title: Rewards
        content.addView(text("Rewards", 18, true, TEXT).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_section_title
            setPadding(dp(2), dp(14), dp(2), dp(10))
        })

        // Reward Host
        rewardHost = LinearLayout(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_host
            orientation = LinearLayout.VERTICAL
        }
        content.addView(rewardHost)
    }

    /** Date shown under an event name: the backend's date with the time-of-day and zone stripped (never a raw ISO stamp). */
    private fun eventDateLabel(event: OrganizerMvpEvent): String =
        organizerEventDateLine(event.shortDate.takeIf { it.isNotBlank() && it != "-" } ?: event.dateTime.takeIf { it != "-" }.orEmpty(), "", "")

    private fun customEventSelector(
        events: List<OrganizerMvpEvent>,
        selected: OrganizerMvpEvent,
        onSelected: (OrganizerMvpEvent) -> Unit,
    ): View {
        val approvedEvents = events.approvedOnly()
        var selectedIndex = approvedEvents.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        val currentEvent = approvedEvents.getOrNull(selectedIndex) ?: selected

        val titleText = text(currentEvent.title.ifBlank { "Select Event" }, 15, true, TEXT).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitleText = text(
            eventDateLabel(currentEvent),
            13,
            false,
            MUTED,
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_drop_down)
            setColorFilter(MUTED)
            contentDescription = "Select event"
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.parseColor("#F8F9FE"), 12, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(12)
                }
                background = rounded(Color.parseColor("#EEF2FF"), 10, null, density = resources.displayMetrics.density)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(PURPLE)
                contentDescription = null
            })

            addView(LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(titleText)
                if (subtitleText.text.isNotBlank()) {
                    addView(subtitleText)
                }
            })

            addView(arrow.apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
        }

        var popup: android.widget.PopupWindow? = null

        fun buildDropdown(): LinearLayout = LinearLayout(this@ManageRewardsActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 12, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            approvedEvents.forEachIndexed { index, event ->
                val isCurrent = index == selectedIndex
                addView(LinearLayout(this@ManageRewardsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    setBackgroundColor(if (isCurrent) Color.parseColor("#EEF2FF") else Color.WHITE)
                    setOnClickListener {
                        selectedIndex = index
                        val sel = approvedEvents[index]
                        titleText.text = sel.title.ifBlank { "Untitled Event" }
                        subtitleText.text = eventDateLabel(sel)
                        popup?.dismiss()
                        onSelected(sel)
                    }
                    addView(LinearLayout(this@ManageRewardsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        addView(text(event.title.ifBlank { "Untitled Event" }, 15, true, if (isCurrent) PURPLE else TEXT))
                        val dateStr = eventDateLabel(event)
                        if (dateStr.isNotBlank()) {
                            addView(text(dateStr, 12, false, MUTED))
                        }
                    })
                })
            }
        }

        box.setOnClickListener {
            if (approvedEvents.isEmpty()) return@setOnClickListener
            popup?.dismiss()
            // A long event list must scroll inside the popup instead of running off the screen.
            val maxHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
            popup = android.widget.PopupWindow(
                android.widget.ScrollView(this@ManageRewardsActivity).apply { addView(buildDropdown()) },
                box.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
                if (approvedEvents.size > 6) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).apply {
                isOutsideTouchable = true
                elevation = dp(8).toFloat()
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            }
            popup.showAsDropDown(box, 0, dp(4))
        }

        return box
    }

    private fun bindEventSummary() {
        rewardsEnabledSwitch.setOnCheckedChangeListener(null)
        rewardsEnabledSwitch.isChecked = rewardsEnabled
        rewardsEnabledSwitch.setOnCheckedChangeListener { _, checked -> setRewardsEnabled(checked) }
    }

    private fun loadRewards(showInitialLoading: Boolean = true) {
        if (showInitialLoading && !refreshLayout.isRefreshing) {
            rewardHost.removeAllViews()
            rewardHost.addView(text("Loading rewards...", 14, false, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(24))
            })
        }

        val eventId = selectedEvent.id
        lifecycleScope.launch {
            try {
                val rewardsResponse = rewardsService.getRewards(eventId)
                val redemptionsResponse = rewardsService.getClaimedRewards(eventId)
                if (selectedEvent.id != eventId) {
                    refreshLayout.isRefreshing = false
                    return@launch
                }
                if (!rewardsResponse.success) {
                    throw IllegalStateException(rewardsResponse.message ?: "Unable to load rewards.")
                }
                rewards = rewardsResponse.data.orEmpty().sortedBy { it.name.lowercase() }
                redemptions = redemptionsResponse.data.orEmpty()
                refreshLayout.isRefreshing = false
                renderRewards()
            } catch (error: Exception) {
                if (selectedEvent.id != eventId) return@launch
                refreshLayout.isRefreshing = false
                rewardHost.removeAllViews()
                rewardHost.addView(errorState(error.message ?: "Unable to load rewards.") { loadRewards() })
                Toast.makeText(this@ManageRewardsActivity, error.message ?: "Unable to load rewards.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderRewards() {
        bindEventSummary()
        rewardHost.removeAllViews()
        val enabled = rewardsEnabled
        if (rewards.isEmpty()) {
            rewardHost.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(24), dp(32), dp(24))
                addView(ImageView(this@ManageRewardsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                    setImageResource(R.drawable.ic_nav_gift)
                    setColorFilter(resources.getColor(R.color.text_disabled, theme))
                    contentDescription = null
                })
                addView(text("No rewards created yet", 16, true, resources.getColor(R.color.text_primary, theme)).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(4))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                })
                addView(text("Create rewards to let attendees redeem perks during the event.", 14, false, resources.getColor(R.color.text_secondary, theme)).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                })
                addView(primaryButton("Add Reward") { showRewardDialog(null) }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(48),
                    ).apply {
                        setMargins(0, dp(16), 0, 0)
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                })
            })
            return
        }

        rewards.forEach { reward ->
            rewardHost.addView(rewardCard(reward, enabled))
        }
    }

    private fun rewardCard(reward: RewardResponse, rewardsEnabled: Boolean): LinearLayout {
        val stockState = RewardCardState.of(reward)
        val outOfStock = stockState.outOfStock
        val active = rewardsEnabled && reward.status == RewardStatus.ACTIVE && !outOfStock
        val badgeText = when {
            !rewardsEnabled || reward.status == RewardStatus.INACTIVE -> "Disabled"
            outOfStock -> getString(R.string.reward_details_out_of_stock)
            else -> "Available"
        }
        val (badgeBg, badgeTextColor, dotColor) = when (badgeText) {
            "Available" -> Triple(Color.parseColor("#DCFCE7"), Color.parseColor("#047857"), Color.parseColor("#10B981"))
            getString(R.string.reward_details_out_of_stock) -> Triple(Color.parseColor("#FEE2E2"), Color.parseColor("#B91C1C"), Color.parseColor("#EF4444"))
            else -> Triple(Color.parseColor("#F3F4F6"), Color.parseColor("#374151"), Color.parseColor("#9CA3AF"))
        }

        return card(16).apply {
            alpha = if (active) 1f else 0.82f

            val topRow = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Left Icon Tile
            topRow.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    marginEnd = dp(12)
                }
                background = rounded(Color.parseColor("#EEF2FF"), 12, null, density = resources.displayMetrics.density)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setImageResource(R.drawable.ic_nav_gift)
                setColorFilter(PURPLE)
                contentDescription = null
            })

            // Middle Column
            val middleCol = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
            }

            middleCol.addView(text(reward.name, 16, true, TEXT).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_name
            })

            val pointsRow = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(2))
            }
            pointsRow.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                    marginEnd = dp(4)
                }
                setImageResource(R.drawable.ic_gift)
                setColorFilter(MUTED)
                contentDescription = null
            })
            pointsRow.addView(text(resources.getQuantityString(R.plurals.manage_rewards_cost_pts, reward.pointsRequired, formatCount(reward.pointsRequired)), 13, false, MUTED).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_points
            })
            middleCol.addView(pointsRow)

            val claimedStr = rewardClaimedText(this@ManageRewardsActivity, stockState)
            middleCol.addView(text(claimedStr, 12, false, MUTED).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_stock
            })

            topRow.addView(middleCol)

            // Right Side: Badge + Chevron
            val rightCol = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val badgePill = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = rounded(badgeBg, 16, null, density = resources.displayMetrics.density)

                addView(View(this@ManageRewardsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                        marginEnd = dp(6)
                    }
                    background = rounded(dotColor, 3, null, density = resources.displayMetrics.density)
                })

                addView(text(badgeText, 12, true, badgeTextColor))
            }
            rightCol.addView(badgePill)

            rightCol.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    marginStart = dp(6)
                }
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(MUTED)
                contentDescription = null
            })

            topRow.addView(rightCol)
            addView(topRow)

            if (reward.allowDuplicateClaims) {
                addView(text("Attendees may claim this reward more than once", 12, false, PURPLE).apply {
                    setPadding(0, dp(8), 0, 0)
                })
            }

            // Action Buttons
            val actions = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dp(14), 0, 0) }
            }

            val editBtn = buildActionButton(
                label = "Edit",
                iconRes = R.drawable.ic_edit_pencil,
                bgColor = Color.parseColor("#EEF2FF"),
                textColor = PURPLE,
                iconTint = PURPLE,
                onClick = { showRewardDialog(reward) }
            ).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_edit
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    marginEnd = dp(6)
                }
            }

            val removeBtn = buildActionButton(
                label = "Remove",
                iconRes = R.drawable.ic_trash,
                bgColor = Color.parseColor("#8B1D2C"),
                textColor = Color.WHITE,
                iconTint = Color.WHITE,
                onClick = { confirmDeleteReward(reward) }
            ).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_remove
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    marginStart = dp(6)
                }
            }

            actions.addView(editBtn)
            actions.addView(removeBtn)
            addView(actions)
        }
    }

    private fun buildActionButton(
        label: String,
        iconRes: Int,
        bgColor: Int,
        textColor: Int,
        iconTint: Int,
        onClick: () -> Unit,
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(bgColor, 10, null, density = resources.displayMetrics.density)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    marginEnd = dp(6)
                }
                setImageResource(iconRes)
                setColorFilter(iconTint)
                contentDescription = null
            })

            addView(text(label, 14, true, textColor).apply {
                gravity = Gravity.CENTER
            })
        }
    }

    private fun showRewardDialog(reward: RewardResponse?) {
        val isEdit = reward != null
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val titleInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_title_input
            hint = "e.g. Coffee Voucher"
            setText(reward?.name.orEmpty())
            isSingleLine = true
        }
        val pointsInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_points_input
            hint = "e.g. 100"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(reward?.pointsRequired?.toString().orEmpty())
            isSingleLine = true
        }
        val quantityInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_quantity_input
            inputType = InputType.TYPE_CLASS_NUMBER
            // Total supply, never the remaining stock; blank means unlimited.
            setText(reward?.totalQuantity?.toString().orEmpty())
            hint = getString(R.string.manage_rewards_quantity_hint)
            isSingleLine = true
        }
        val descriptionInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_description_input
            hint = getString(R.string.manage_rewards_description_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(reward?.description.orEmpty())
            minLines = 2
        }
        val duplicateSwitch = SwitchCompat(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_duplicate_switch
            text = "Allow duplicate claims"
            isChecked = reward?.allowDuplicateClaims == true
            setTextColor(0xFF151A2D.toInt())
            setPadding(dp(2), dp(8), dp(2), dp(8))
        }
        form.addView(fieldLabel("Reward Title"))
        form.addView(titleInput)
        form.addView(fieldLabel("Points Cost"))
        form.addView(pointsInput)
        form.addView(fieldLabel(getString(R.string.manage_rewards_total_quantity)))
        form.addView(quantityInput)
        form.addView(fieldLabel(getString(R.string.manage_rewards_description)))
        form.addView(descriptionInput)
        form.addView(fieldLabel("Settings"))
        form.addView(duplicateSwitch)

        AlertDialog.Builder(this)
            .setTitle(getString(if (isEdit) R.string.manage_rewards_edit_reward else R.string.manage_rewards_create_reward))
            .setView(form)
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            .setPositiveButton(getString(if (isEdit) R.string.manage_scan_purposes_save else R.string.manage_rewards_create)) { _, _ ->
                val title = titleInput.text.toString().trim()
                val points = pointsInput.text.toString().toIntOrNull()
                val quantity = resolveRewardQuantity(quantityInput.text.toString(), reward?.let { it.totalQuantity?.toString().orEmpty() })
                if (title.isBlank() || points == null || points <= 0 || quantity is RewardQuantity.Invalid) {
                    Toast.makeText(this, this.getString(R.string.manage_rewards_enter_a_valid_reward_title_points_co), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveReward(
                    reward,
                    title,
                    points,
                    quantity,
                    resolveRewardDescription(descriptionInput.text.toString(), reward?.description, isEdit),
                    duplicateSwitch.isChecked,
                )
            }
            .show()
    }

    private fun fieldLabel(label: String): TextView = text(label, 13, true, TEXT).apply {
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun saveReward(
        existingReward: RewardResponse?,
        title: String,
        points: Int,
        quantity: RewardQuantity,
        description: String?,
        allowDuplicateClaims: Boolean,
    ) {
        val eventId = selectedEvent.id
        val request = buildRewardRequest(UUID.fromString(eventId), title, points, quantity, description, allowDuplicateClaims)
        lifecycleScope.launch {
            try {
                val response = if (existingReward == null) {
                    rewardsService.createReward(eventId, request)
                } else {
                    rewardsService.updateReward(eventId, existingReward.rewardId.toString(), request)
                }
                if (!response.success) throw IllegalStateException(response.message ?: "Reward could not be saved.")
                Toast.makeText(this@ManageRewardsActivity, response.message ?: "Reward saved.", Toast.LENGTH_SHORT).show()
                loadRewards()
            } catch (error: Exception) {
                val serverMessage = (error as? retrofit2.HttpException)?.let { com.thedavelopers.eventqr.core.api.parseHttpErrorMessage(it) }
                Toast.makeText(this@ManageRewardsActivity, serverMessage ?: error.message ?: "Reward could not be saved.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDeleteReward(reward: RewardResponse) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_rewards_remove_reward))
            .setMessage("Remove ${reward.name} from ${selectedEvent.title}?")
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            .setPositiveButton(getString(R.string.staff_remove)) { _, _ -> deleteReward(reward) }
            .show()
    }

    private fun deleteReward(reward: RewardResponse) {
        val eventId = selectedEvent.id
        lifecycleScope.launch {
            try {
                val response = rewardsService.deleteReward(eventId, reward.rewardId.toString())
                if (!response.success) throw IllegalStateException(response.message ?: "Reward could not be removed.")
                Toast.makeText(this@ManageRewardsActivity, response.message ?: "Reward removed.", Toast.LENGTH_SHORT).show()
                loadRewards()
            } catch (error: Exception) {
                Toast.makeText(this@ManageRewardsActivity, error.message ?: "Reward could not be removed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshRewardsEnabledFromServer() {
        val eventId = selectedEvent.id
        lifecycleScope.launch {
            try {
                val response = rewardsService.getRewardSettings(eventId)
                if (selectedEvent.id != eventId) return@launch
                if (response.success && response.data != null) {
                    rewardsEnabled = response.data
                    bindEventSummary()
                    renderRewards()
                }
            } catch (_: Exception) {
                // Server truth will be re-synced on the next refresh; keep current local state.
            }
        }
    }

    private fun setRewardsEnabled(enabled: Boolean) {
        val eventId = selectedEvent.id
        lifecycleScope.launch {
            try {
                val response = rewardsService.updateRewardSettings(eventId, RewardSettingsRequest(enabled))
                if (selectedEvent.id != eventId) return@launch
                if (!response.success) throw IllegalStateException(response.message ?: "Could not update reward settings.")
                response.data?.let { rewardsEnabled = it.rewardsEnabled }
                bindEventSummary()
                renderRewards()
                Toast.makeText(
                    this@ManageRewardsActivity,
                    getString(if (rewardsEnabled) R.string.manage_rewards_reward_redemption_enabled_for_this_e else R.string.manage_rewards_reward_redemption_disabled_for_this),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: Exception) {
                rewardsEnabledSwitch.setOnCheckedChangeListener(null)
                rewardsEnabledSwitch.isChecked = rewardsEnabled
                rewardsEnabledSwitch.setOnCheckedChangeListener { _, checked -> setRewardsEnabled(checked) }
                Toast.makeText(
                    this@ManageRewardsActivity,
                    "Could not update reward settings: ${error.message ?: "try again."}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

private interface OrganizerRewardsService {
    @GET("organizer/events/{eventId}/rewards")
    suspend fun getRewards(@Path("eventId") eventId: String): ApiResponse<List<RewardResponse>>

    @GET("organizer/events/{eventId}/reward-settings")
    suspend fun getRewardSettings(@Path("eventId") eventId: String): ApiResponse<Boolean>

    @PATCH("organizer/events/{eventId}/reward-settings")
    suspend fun updateRewardSettings(
        @Path("eventId") eventId: String,
        @Body request: RewardSettingsRequest,
    ): ApiResponse<EventResponse>

    @POST("organizer/events/{eventId}/rewards")
    suspend fun createReward(
        @Path("eventId") eventId: String,
        @Body request: RewardRequest,
    ): ApiResponse<RewardResponse>

    @PATCH("organizer/events/{eventId}/rewards/{rewardId}")
    suspend fun updateReward(
        @Path("eventId") eventId: String,
        @Path("rewardId") rewardId: String,
        @Body request: RewardRequest,
    ): ApiResponse<RewardResponse>

    @DELETE("organizer/events/{eventId}/rewards/{rewardId}")
    suspend fun deleteReward(
        @Path("eventId") eventId: String,
        @Path("rewardId") rewardId: String,
    ): ApiResponse<Unit>

    @GET("organizer/events/{eventId}/claimed-rewards")
    suspend fun getClaimedRewards(@Path("eventId") eventId: String): ApiResponse<List<RewardRedemptionResponse>>
}

data class RewardSettingsRequest(
    val enabled: Boolean,
)

private object OrganizerRewardsApiProvider {
    @Volatile
    private var service: OrganizerRewardsService? = null

    fun get(context: Context): OrganizerRewardsService {
        return service ?: synchronized(this) {
            service ?: build(context.applicationContext).also { service = it }
        }
    }

    private fun build(context: Context): OrganizerRewardsService {
        val client = com.thedavelopers.eventqr.core.api.ApiClient.newHttpClient(context)
        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(OrganizerRewardsService::class.java)
    }
}
