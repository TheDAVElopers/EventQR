package com.thedavelopers.eventqr.features.organizer.rewards

import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenFloat
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.features.organizer.orgColors
import android.app.AlertDialog
import android.content.Context
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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.ApiConfig
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse
import com.thedavelopers.eventqr.features.organizer.NAV_REWARDS
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.organizerEventDateLine
import com.thedavelopers.eventqr.features.organizer.approvedOnly
import com.thedavelopers.eventqr.features.organizer.cardRes
import com.thedavelopers.eventqr.features.organizer.centeredEmptyState
import com.thedavelopers.eventqr.features.organizer.errorState
import com.thedavelopers.eventqr.features.organizer.eventsLoadErrorState
import com.thedavelopers.eventqr.features.organizer.formatCount
import com.thedavelopers.eventqr.features.organizer.intentEventId
import com.thedavelopers.eventqr.features.organizer.organizerRefreshShell
import com.thedavelopers.eventqr.features.organizer.primaryButton
import com.thedavelopers.eventqr.features.organizer.resolveSelectedEvent
import com.thedavelopers.eventqr.features.organizer.saveSelectedEventId
import com.thedavelopers.eventqr.features.organizer.selectedEventId
import com.thedavelopers.eventqr.features.organizer.styledText
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
    @VisibleForTesting
    internal lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var content: LinearLayout
    private lateinit var rewardsEnabledSwitch: SwitchCompat
    private lateinit var rewardHost: LinearLayout
    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    /** Tests set this before the first rewards call to replace the network service. */
    @VisibleForTesting
    internal var rewardsServiceOverride: OrganizerRewardsService? = null
    private val rewardsService by lazy { rewardsServiceOverride ?: OrganizerRewardsApiProvider.get(this) }
    private var rewardDialog: AlertDialog? = null
    private var rewards: List<RewardResponse> = emptyList()
    private var redemptions: List<RewardRedemptionResponse> = emptyList()
    private var eventOptions: List<OrganizerMvpEvent> = emptyList()
    private var rewardsEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)

        val shell = organizerRefreshShell(
            title = getString(R.string.manage_rewards_title),
            selectedNav = NAV_REWARDS,
            showBack = false,
            topRightLabel = null,
            onTopRight = { showRewardDialog(null) },
            // Before an event is resolved (e.g. the event list failed to load) a pull retries that load instead.
            onRefresh = { if (::selectedEvent.isInitialized) loadRewards(showInitialLoading = false) else loadEvents() },
        )
        content = shell.content
        refreshLayout = shell.swipeRefreshLayout
        // Nothing to refresh until an event is resolved; re-enabled below once the event list loads.
        refreshLayout.isEnabled = false

        loadEvents()
    }

    private fun loadEvents() {
        lifecycleScope.launch { onEventsLoaded(repository.loadApprovedOrganizerEvents()) }
    }

    @VisibleForTesting
    internal fun onEventsLoaded(eventsLoad: OrganizerMvpLoad<List<OrganizerMvpEvent>>) {
        refreshLayout.isRefreshing = false
        // A failed fetch is not "no events": show the error with Retry (pull-to-refresh retries too).
        if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
            refreshLayout.isEnabled = true
            content.removeAllViews()
            content.addView(eventsLoadErrorState(eventsLoad.message) {
                if (refreshLayout.isRefreshing) return@eventsLoadErrorState
                refreshLayout.isRefreshing = true
                content.removeAllViews()
                loadEvents()
            })
            content.setBackgroundColor(orgColors.bg)
            return
        }
        eventOptions = eventsLoad.data
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
                title = getString(R.string.manage_rewards_no_events_title),
                subtext = getString(R.string.manage_rewards_no_events_subtext),
            ))
            content.setBackgroundColor(orgColors.bg)
        }
    }

    @VisibleForTesting
    internal fun buildScreen() {
        content.removeAllViews()
        content.setBackgroundColor(orgColors.bg)

        // Card 1: SELECT EVENT
        content.addView(cardRes(R.dimen.space_lg).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_event_selector
            addView(styledText(getString(R.string.organizer_select_event_header), R.dimen.type_overline, true, color(R.color.text_label)).apply {
                letterSpacing = dimenFloat(R.dimen.tracking_label_05)
                setPadding(0, 0, 0, dimenPx(R.dimen.space_sm))
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
        content.addView(cardRes(R.dimen.space_lg).apply {
            val row = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_42), dimenPx(R.dimen.tile_42)).apply {
                    marginEnd = dimenPx(R.dimen.space_md)
                }
                background = roundedRect(color(R.color.success_container), R.dimen.radius_md)
                setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10))
                setImageResource(R.drawable.ic_nav_gift)
                setColorFilter(color(R.color.success_dark))
                contentDescription = null
            })

            row.addView(LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dimenPx(R.dimen.space_sm)
                }
                addView(styledText(getString(R.string.manage_rewards_event_rewards), R.dimen.type_body_large, true, orgColors.text).apply {
                    id = com.thedavelopers.eventqr.R.id.mrw_rewards_label
                })
                addView(styledText(getString(R.string.manage_rewards_event_rewards_desc), R.dimen.type_body_compact, false, orgColors.muted).apply {
                    id = com.thedavelopers.eventqr.R.id.mrw_rewards_desc
                    setPadding(0, dimenPx(R.dimen.space_xxs), 0, 0)
                })
            })

            rewardsEnabledSwitch = SwitchCompat(this@ManageRewardsActivity).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_rewards_switch
                isChecked = rewardsEnabled
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                val thumbColors = intArrayOf(color(R.color.surface), color(R.color.surface))
                val trackColors = intArrayOf(color(R.color.success), color(R.color.outline_strong))
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
        content.addView(styledText(getString(R.string.manage_rewards_title), R.dimen.type_title, true, orgColors.text).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_section_title
            setPadding(dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_10))
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

        val titleText = styledText(currentEvent.title.ifBlank { getString(R.string.manage_rewards_select_event_title) }, R.dimen.type_body, true, orgColors.text).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitleText = styledText(
            eventDateLabel(currentEvent),
            R.dimen.type_body_compact,
            false,
            orgColors.muted,
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_drop_down)
            setColorFilter(orgColors.muted)
            contentDescription = getString(R.string.manage_rewards_select_event)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10))
            background = roundedRect(color(R.color.accent_container), R.dimen.radius_md, color(R.color.outline))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_md), dimenPx(R.dimen.icon_tile_md)).apply {
                    marginEnd = dimenPx(R.dimen.space_md)
                }
                background = roundedRect(color(R.color.accent_container), R.dimen.radius_10)
                setPadding(dimenPx(R.dimen.space_9), dimenPx(R.dimen.space_9), dimenPx(R.dimen.space_9), dimenPx(R.dimen.space_9))
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(orgColors.primary)
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
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_22), dimenPx(R.dimen.icon_22))
            })
        }

        var popup: android.widget.PopupWindow? = null

        fun buildDropdown(): LinearLayout = LinearLayout(this@ManageRewardsActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(color(R.color.surface), R.dimen.radius_md, color(R.color.outline))
            approvedEvents.forEachIndexed { index, event ->
                val isCurrent = index == selectedIndex
                addView(LinearLayout(this@ManageRewardsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md))
                    setBackgroundColor(if (isCurrent) color(R.color.accent_container) else color(R.color.surface))
                    setOnClickListener {
                        selectedIndex = index
                        val sel = approvedEvents[index]
                        titleText.text = sel.title.ifBlank { getString(R.string.manage_rewards_untitled_event) }
                        subtitleText.text = eventDateLabel(sel)
                        popup?.dismiss()
                        onSelected(sel)
                    }
                    addView(LinearLayout(this@ManageRewardsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        addView(styledText(event.title.ifBlank { getString(R.string.manage_rewards_untitled_event) }, R.dimen.type_body, true, if (isCurrent) orgColors.primary else orgColors.text))
                        val dateStr = eventDateLabel(event)
                        if (dateStr.isNotBlank()) {
                            addView(styledText(dateStr, R.dimen.type_caption, false, orgColors.muted))
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
                elevation = dimenPx(R.dimen.elevation_overlay).toFloat()
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color(R.color.transparent)))
            }
            popup.showAsDropDown(box, 0, dimenPx(R.dimen.space_xs))
        }

        return box
    }

    private fun bindEventSummary() {
        rewardsEnabledSwitch.setOnCheckedChangeListener(null)
        rewardsEnabledSwitch.isChecked = rewardsEnabled
        rewardsEnabledSwitch.setOnCheckedChangeListener { _, checked -> setRewardsEnabled(checked) }
    }

    @VisibleForTesting
    internal fun loadRewards(showInitialLoading: Boolean = true) {
        // A refresh can arrive before the event list has resolved an event; there is nothing to load yet.
        if (!::selectedEvent.isInitialized) {
            refreshLayout.isRefreshing = false
            return
        }
        if (showInitialLoading && !refreshLayout.isRefreshing) {
            rewardHost.removeAllViews()
            rewardHost.addView(styledText(getString(R.string.manage_rewards_loading), R.dimen.type_body_medium, false, orgColors.muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, dimenPx(R.dimen.space_xl), 0, dimenPx(R.dimen.space_xl))
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
                    throw IllegalStateException(rewardsResponse.message ?: getString(R.string.manage_rewards_load_failed))
                }
                rewards = rewardsResponse.data.orEmpty().sortedBy { it.name.lowercase() }
                redemptions = redemptionsResponse.data.orEmpty()
                refreshLayout.isRefreshing = false
                renderRewards()
            } catch (error: Exception) {
                if (selectedEvent.id != eventId) return@launch
                refreshLayout.isRefreshing = false
                rewardHost.removeAllViews()
                rewardHost.addView(errorState(error.message ?: getString(R.string.manage_rewards_load_failed)) { loadRewards() })
                Toast.makeText(this@ManageRewardsActivity, error.message ?: getString(R.string.manage_rewards_load_failed), Toast.LENGTH_SHORT).show()
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
                setPadding(dimenPx(R.dimen.space_xxl), dimenPx(R.dimen.space_xl), dimenPx(R.dimen.space_xxl), dimenPx(R.dimen.space_xl))
                addView(ImageView(this@ManageRewardsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_64), dimenPx(R.dimen.tile_64))
                    setImageResource(R.drawable.ic_nav_gift)
                    setColorFilter(resources.getColor(R.color.neutral, theme))
                    contentDescription = null
                })
                addView(styledText(getString(R.string.manage_rewards_empty_title), R.dimen.type_body_large, true, resources.getColor(R.color.text_primary, theme)).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_xs))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                })
                addView(styledText(getString(R.string.manage_rewards_empty_subtext), R.dimen.type_body_medium, false, resources.getColor(R.color.text_secondary, theme)).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                })
                addView(primaryButton(getString(R.string.manage_rewards_add_reward)) { showRewardDialog(null) }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dimenPx(R.dimen.touch_target_min),
                    ).apply {
                        setMargins(0, dimenPx(R.dimen.space_lg), 0, 0)
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

    private enum class RewardBadge { DISABLED, OUT_OF_STOCK, AVAILABLE }

    private fun rewardCard(reward: RewardResponse, rewardsEnabled: Boolean): LinearLayout {
        val stockState = RewardCardState.of(reward)
        val outOfStock = stockState.outOfStock
        val active = rewardsEnabled && reward.status == RewardStatus.ACTIVE && !outOfStock
        val badgeKind = when {
            !rewardsEnabled || reward.status == RewardStatus.INACTIVE -> RewardBadge.DISABLED
            outOfStock -> RewardBadge.OUT_OF_STOCK
            else -> RewardBadge.AVAILABLE
        }
        val badgeText = getString(
            when (badgeKind) {
                RewardBadge.DISABLED -> R.string.manage_rewards_badge_disabled
                RewardBadge.OUT_OF_STOCK -> R.string.reward_details_out_of_stock
                RewardBadge.AVAILABLE -> R.string.manage_rewards_badge_available
            }
        )
        val (badgeBg, badgeTextColor, dotColor) = when (badgeKind) {
            RewardBadge.AVAILABLE -> Triple(color(R.color.success_container), color(R.color.on_success_container), color(R.color.success))
            RewardBadge.OUT_OF_STOCK -> Triple(color(R.color.error_container), color(R.color.error), color(R.color.danger))
            RewardBadge.DISABLED -> Triple(color(R.color.surface_muted), color(R.color.text_body), color(R.color.neutral))
        }

        return cardRes(R.dimen.space_lg).apply {
            alpha = if (active) 1f else dimenFloat(R.dimen.alpha_inactive)

            val topRow = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Left Icon Tile
            topRow.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44)).apply {
                    marginEnd = dimenPx(R.dimen.space_md)
                }
                background = roundedRect(color(R.color.accent_container), R.dimen.radius_md)
                setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10))
                setImageResource(R.drawable.ic_nav_gift)
                setColorFilter(orgColors.primary)
                contentDescription = null
            })

            // Middle Column
            val middleCol = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dimenPx(R.dimen.space_sm)
                }
            }

            middleCol.addView(styledText(reward.name, R.dimen.type_body_large, true, orgColors.text).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_name
            })

            val pointsRow = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dimenPx(R.dimen.space_3), 0, dimenPx(R.dimen.space_xxs))
            }
            pointsRow.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_14), dimenPx(R.dimen.icon_14)).apply {
                    marginEnd = dimenPx(R.dimen.space_xs)
                }
                setImageResource(R.drawable.ic_gift)
                setColorFilter(orgColors.muted)
                contentDescription = null
            })
            pointsRow.addView(styledText(resources.getQuantityString(R.plurals.manage_rewards_cost_pts, reward.pointsRequired, formatCount(reward.pointsRequired)), R.dimen.type_body_compact, false, orgColors.muted).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_points
            })
            middleCol.addView(pointsRow)

            val claimedStr = rewardClaimedText(this@ManageRewardsActivity, stockState)
            middleCol.addView(styledText(claimedStr, R.dimen.type_caption, false, orgColors.muted).apply {
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
                setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs))
                background = roundedRect(badgeBg, R.dimen.radius_lg)

                addView(View(this@ManageRewardsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.progress_bar_height), dimenPx(R.dimen.progress_bar_height)).apply {
                        marginEnd = dimenPx(R.dimen.space_6)
                    }
                    background = roundedRect(dotColor, R.dimen.radius_3)
                })

                addView(styledText(badgeText, R.dimen.type_caption, true, badgeTextColor))
            }
            rightCol.addView(badgePill)

            rightCol.addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18)).apply {
                    marginStart = dimenPx(R.dimen.space_6)
                }
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(orgColors.muted)
                contentDescription = null
            })

            topRow.addView(rightCol)
            addView(topRow)

            if (reward.allowDuplicateClaims) {
                addView(styledText(getString(R.string.manage_rewards_duplicate_claims_note), R.dimen.type_caption, false, orgColors.primary).apply {
                    setPadding(0, dimenPx(R.dimen.space_sm), 0, 0)
                })
            }

            // Action Buttons
            val actions = LinearLayout(this@ManageRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dimenPx(R.dimen.space_14), 0, 0) }
            }

            val editBtn = buildActionButton(
                label = getString(R.string.manage_rewards_edit),
                iconRes = R.drawable.ic_edit_pencil,
                bgColor = color(R.color.accent_container),
                textColor = orgColors.primary,
                iconTint = orgColors.primary,
                onClick = { showRewardDialog(reward) }
            ).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_edit
                layoutParams = LinearLayout.LayoutParams(0, dimenPx(R.dimen.tile_44), 1f).apply {
                    marginEnd = dimenPx(R.dimen.space_6)
                }
            }

            val removeBtn = buildActionButton(
                label = getString(R.string.manage_rewards_remove),
                iconRes = R.drawable.ic_trash,
                bgColor = color(R.color.deco_destructive_deep),
                textColor = color(R.color.on_primary),
                iconTint = color(R.color.on_primary),
                onClick = { confirmDeleteReward(reward) }
            ).apply {
                id = com.thedavelopers.eventqr.R.id.mrw_reward_remove
                layoutParams = LinearLayout.LayoutParams(0, dimenPx(R.dimen.tile_44), 1f).apply {
                    marginStart = dimenPx(R.dimen.space_6)
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
            setPadding(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10))
            background = roundedRect(bgColor, R.dimen.radius_10)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(ImageView(this@ManageRewardsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18)).apply {
                    marginEnd = dimenPx(R.dimen.space_6)
                }
                setImageResource(iconRes)
                setColorFilter(iconTint)
                contentDescription = null
            })

            addView(styledText(label, R.dimen.type_body_medium, true, textColor).apply {
                gravity = Gravity.CENTER
            })
        }
    }

    @VisibleForTesting
    internal fun showRewardDialog(reward: RewardResponse?) {
        val isEdit = reward != null
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_20), 0)
        }
        val titleInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_title_input
            hint = getString(R.string.manage_rewards_title_hint)
            setText(reward?.name.orEmpty())
            isSingleLine = true
        }
        val pointsInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.mrw_reward_points_input
            hint = getString(R.string.manage_rewards_points_hint)
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
            text = getString(R.string.manage_rewards_allow_duplicate_claims)
            isChecked = reward?.allowDuplicateClaims == true
            setTextColor(color(R.color.text_primary))
            setPadding(dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_sm))
        }
        form.addView(fieldLabel(getString(R.string.manage_rewards_reward_title)))
        form.addView(titleInput)
        form.addView(fieldLabel(getString(R.string.manage_rewards_points_cost)))
        form.addView(pointsInput)
        form.addView(fieldLabel(getString(R.string.manage_rewards_total_quantity)))
        form.addView(quantityInput)
        form.addView(fieldLabel(getString(R.string.manage_rewards_description)))
        form.addView(descriptionInput)
        form.addView(fieldLabel(getString(R.string.manage_rewards_settings)))
        form.addView(duplicateSwitch)

        rewardDialog?.dismiss()
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(if (isEdit) R.string.manage_rewards_edit_reward else R.string.manage_rewards_create_reward))
            .setView(form)
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            // Click handling is replaced after show() so invalid input keeps the dialog (and what was typed) open.
            .setPositiveButton(getString(if (isEdit) R.string.manage_scan_purposes_save else R.string.manage_rewards_create), null)
            .show()
        rewardDialog = dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = titleInput.text.toString().trim()
            val points = pointsInput.text.toString().trim().toIntOrNull()
            val quantity = resolveRewardQuantity(quantityInput.text.toString(), reward?.let { it.totalQuantity?.toString().orEmpty() })
            titleInput.error = if (title.isBlank()) getString(R.string.manage_rewards_title_required) else null
            pointsInput.error = if (points == null || points <= 0) getString(R.string.manage_rewards_points_invalid) else null
            quantityInput.error = if (quantity is RewardQuantity.Invalid) getString(R.string.manage_rewards_quantity_invalid) else null
            val firstInvalid = listOf(titleInput, pointsInput, quantityInput).firstOrNull { it.error != null }
            if (firstInvalid != null || points == null) {
                firstInvalid?.requestFocus()
                return@setOnClickListener
            }
            saveReward(
                reward,
                title,
                points,
                quantity,
                resolveRewardDescription(descriptionInput.text.toString(), reward?.description, isEdit),
                duplicateSwitch.isChecked,
                dialog,
            )
        }
    }

    private fun fieldLabel(label: String): TextView = styledText(label, R.dimen.type_body_compact, true, orgColors.text).apply {
        setPadding(0, dimenPx(R.dimen.space_md), 0, dimenPx(R.dimen.space_xs))
    }

    private fun saveReward(
        existingReward: RewardResponse?,
        title: String,
        points: Int,
        quantity: RewardQuantity,
        description: String?,
        allowDuplicateClaims: Boolean,
        dialog: AlertDialog,
    ) {
        val eventId = selectedEvent.id
        val request = buildRewardRequest(UUID.fromString(eventId), title, points, quantity, description, allowDuplicateClaims)
        // The dialog stays open (keeping the typed values) until the server accepts. While the request runs it
        // cannot be saved again or dismissed (Cancel/back/outside), so input is not lost and no duplicate create
        // can be started from a reopened dialog.
        setRewardDialogBusy(dialog, busy = true)
        lifecycleScope.launch {
            try {
                val response = if (existingReward == null) {
                    rewardsService.createReward(eventId, request)
                } else {
                    rewardsService.updateReward(eventId, existingReward.rewardId.toString(), request)
                }
                if (!response.success) throw IllegalStateException(response.message ?: getString(R.string.manage_rewards_save_failed))
                dialog.dismiss()
                Toast.makeText(this@ManageRewardsActivity, response.message ?: getString(R.string.manage_rewards_saved), Toast.LENGTH_SHORT).show()
                loadRewards()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                setRewardDialogBusy(dialog, busy = false)
                val serverMessage = (error as? retrofit2.HttpException)?.let { com.thedavelopers.eventqr.core.api.parseHttpErrorMessage(it) }
                Toast.makeText(this@ManageRewardsActivity, serverMessage ?: error.message ?: getString(R.string.manage_rewards_save_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setRewardDialogBusy(dialog: AlertDialog, busy: Boolean) {
        dialog.setCancelable(!busy)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
    }

    override fun onDestroy() {
        // Avoid leaking the dialog window when the activity goes away (e.g. rotation) mid-edit or mid-save.
        rewardDialog?.dismiss()
        rewardDialog = null
        super.onDestroy()
    }

    private fun confirmDeleteReward(reward: RewardResponse) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_rewards_remove_reward))
            .setMessage(getString(R.string.manage_rewards_remove_confirm, reward.name, selectedEvent.title))
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            .setPositiveButton(getString(R.string.staff_remove)) { _, _ -> deleteReward(reward) }
            .show()
    }

    private fun deleteReward(reward: RewardResponse) {
        val eventId = selectedEvent.id
        lifecycleScope.launch {
            try {
                val response = rewardsService.deleteReward(eventId, reward.rewardId.toString())
                if (!response.success) throw IllegalStateException(response.message ?: getString(R.string.manage_rewards_remove_failed))
                Toast.makeText(this@ManageRewardsActivity, response.message ?: getString(R.string.manage_rewards_removed), Toast.LENGTH_SHORT).show()
                loadRewards()
            } catch (error: Exception) {
                Toast.makeText(this@ManageRewardsActivity, error.message ?: getString(R.string.manage_rewards_remove_failed), Toast.LENGTH_LONG).show()
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
                if (!response.success) throw IllegalStateException(response.message ?: getString(R.string.manage_rewards_settings_failed))
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
                    getString(R.string.manage_rewards_update_settings_failed, error.message ?: getString(R.string.common_try_again_lower)),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

@VisibleForTesting
internal interface OrganizerRewardsService {
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
