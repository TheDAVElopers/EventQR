package com.thedavelopers.eventqr.features.organizer.events

import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.features.organizer.styledText
import androidx.lifecycle.lifecycleScope
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.scanpurposes.ManageScanPurposesActivity
import com.thedavelopers.eventqr.features.organizer.staff.ManageUsersActivity
import com.thedavelopers.eventqr.features.organizer.transactions.TransactionRulesActivity
import kotlinx.coroutines.launch

open class EventManagementHubActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
    }

    // Reload on every return to this screen (e.g., after saving edits in
    // EditEventDetailsActivity) so the Registered/Capacity/Available summary never shows
    // stale numbers.
    override fun onResume() {
        super.onResume()
        val eventId = intentEventId() ?: return showMissingEventScreen(getString(R.string.event_management_hub_title))
        renderEvent(eventId)
    }

    private fun renderEvent(eventId: String) {
        val content = organizerShell(getString(R.string.event_management_hub_title), showBack = true)
        content.addView(loadingState(getString(R.string.event_management_hub_loading_details)))

        lifecycleScope.launch {
            val load = repository.loadEventForMvp(eventId)
            val event = load.data
            content.removeAllViews()
            if (event == null) {
                dataSourceBanner(load)?.let { content.addView(it) }
                content.addView(
                    if (load.source == OrganizerMvpDataSource.ERROR) {
                        errorState(load.message ?: getString(R.string.event_management_hub_details_load_failed)) { recreate() }
                    } else {
                        emptyState(
                            iconRes = R.drawable.ic_calendar,
                            title = getString(R.string.event_management_hub_event_not_found),
                            subtext = getString(R.string.event_management_hub_event_not_available),
                            actionLabel = getString(R.string.organizer_open_my_events),
                            onAction = { openOrganizerPage(ManageEventsActivity::class.java) },
                        )
                    },
                )
                return@launch
            }

            content.setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_xl))

            val registeredCount = event.currentAttendeeCount.coerceAtLeast(0)
            val capacity = event.capacity.coerceAtLeast(0)
            val available = (capacity - registeredCount).coerceAtLeast(0)

            // Header Banner Card
            val bannerCard = LinearLayout(this@EventManagementHubActivity).apply {
                id = R.id.emh_header_banner
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, dimenPx(R.dimen.space_lg))
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        color(R.color.deco_hub_hero_start),
                        color(R.color.primary_dark),
                        color(R.color.primary),
                    ),
                ).apply {
                    cornerRadius = dimenPx(R.dimen.radius_lg).toFloat()
                }
                setPadding(dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20))

                val leftColumn = LinearLayout(this@EventManagementHubActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )

                    // Badge Pill
                    val statusText = event.lifecycleStatus()
                    val badgeColor = color(R.color.on_primary)
                    val badgeBackground = color(R.color.on_dark_20)
                    val badgeLayout = LinearLayout(this@EventManagementHubActivity).apply {
                        id = R.id.emh_status_badge
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = roundedRect(badgeBackground, R.dimen.radius_lg)
                        setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_xs))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )

                        addView(ImageView(this@EventManagementHubActivity).apply {
                            setImageResource(R.drawable.ic_calendar)
                            setColorFilter(badgeColor)
                            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_14), dimenPx(R.dimen.icon_14)).apply {
                                marginEnd = dimenPx(R.dimen.space_6)
                            }
                        })

                        addView(styledText(statusText, R.dimen.type_overline, true, badgeColor))
                    }
                    addView(badgeLayout)

                    // Event Title
                    addView(styledText(event.title, R.dimen.type_title_large, true, color(R.color.on_primary)).apply {
                        id = R.id.emh_event_title
                        setPadding(0, dimenPx(R.dimen.space_10), 0, 0)
                    })

                    // Subtitle / Description
                    val descText = event.description.ifBlank {
                        getString(R.string.event_management_hub_default_description)
                    }
                    addView(styledText(descText, R.dimen.type_caption, false, ColorUtils.setAlphaComponent(color(R.color.surface), 220)).apply {
                        setPadding(0, dimenPx(R.dimen.space_xs), 0, 0)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
                addView(leftColumn)

                // Large White Icon on Right
                addView(ImageView(this@EventManagementHubActivity).apply {
                    setImageResource(R.drawable.ic_calendar)
                    setColorFilter(color(R.color.on_primary))
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_64), dimenPx(R.dimen.tile_64)).apply {
                        marginStart = dimenPx(R.dimen.space_md)
                        gravity = Gravity.CENTER_VERTICAL
                    }
                })
            }
            content.addView(bannerCard)

            dataSourceBanner(load)?.let { content.addView(it) }

            // Summary Stats Row
            val statsRow = LinearLayout(this@EventManagementHubActivity).apply {
                id = R.id.emh_stats_row
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, dimenPx(R.dimen.space_lg))
                }

                val stat1 = createStatCard(getString(R.string.event_management_hub_stat_registered), formatCount(registeredCount), R.drawable.ic_profile_person).apply {
                    id = R.id.emh_stat_registered
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginEnd = dimenPx(R.dimen.space_xs) }
                }
                val stat2 = createStatCard(getString(R.string.event_management_hub_stat_capacity), formatCount(capacity), R.drawable.ic_group).apply {
                    id = R.id.emh_stat_capacity
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginStart = dimenPx(R.dimen.space_xs); marginEnd = dimenPx(R.dimen.space_xs) }
                }
                val stat3 = createStatCard(getString(R.string.event_management_hub_stat_available), formatCount(available), R.drawable.ic_row_star).apply {
                    id = R.id.emh_stat_available
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginStart = dimenPx(R.dimen.space_xs) }
                }

                addView(stat1)
                addView(stat2)
                addView(stat3)
            }
            content.addView(statsRow)

            // Section Title
            content.addView(styledText(getString(R.string.event_management_hub_section_title), R.dimen.type_body_large, true, color(R.color.text_primary)).apply {
                id = R.id.emh_section_title
                setPadding(dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_md))
            })

            // Menu Items List
            val canEdit = event.lifecycleStatus() == "Upcoming"
            val editLabel = getString(if (canEdit) R.string.event_management_hub_edit_event_details else R.string.event_management_hub_view_event_details)
            val menuItems = listOf(
                MenuSpec(
                    label = editLabel,
                    subtitle = getString(R.string.event_management_hub_menu_edit_sub),
                    iconRes = if (canEdit) R.drawable.ic_edit_pencil else R.drawable.ic_event_request,
                    target = EditEventDetailsActivity::class.java,
                    id = R.id.emh_menu_edit,
                ),
                MenuSpec(
                    label = getString(R.string.event_management_hub_menu_staff),
                    subtitle = getString(R.string.event_management_hub_menu_staff_sub),
                    iconRes = R.drawable.ic_admin_users,
                    target = ManageUsersActivity::class.java,
                    id = R.id.emh_menu_staff,
                ),
                MenuSpec(
                    label = getString(R.string.event_management_hub_menu_scan),
                    subtitle = getString(R.string.event_management_hub_menu_scan_sub),
                    iconRes = R.drawable.ic_scan,
                    target = ManageScanPurposesActivity::class.java,
                    id = R.id.emh_menu_scan,
                ),
                MenuSpec(
                    label = getString(R.string.event_management_hub_menu_transaction),
                    subtitle = getString(R.string.event_management_hub_menu_transaction_sub),
                    iconRes = R.drawable.ic_admin_shield,
                    target = TransactionRulesActivity::class.java,
                    id = R.id.emh_menu_transaction,
                ),
                MenuSpec(
                    label = getString(R.string.event_management_hub_menu_id),
                    subtitle = getString(R.string.event_management_hub_menu_id_sub),
                    iconRes = R.drawable.ic_id,
                    target = com.thedavelopers.eventqr.features.organizer.idtemplate.IdTemplateSettingsActivity::class.java,
                    id = R.id.emh_menu_id,
                ),
            )

            // Menu Items Container (Single card with dividers)
            val menuCardGroup = LinearLayout(this@EventManagementHubActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedRect(color(R.color.surface), R.dimen.radius_md, color(R.color.outline))
                elevation = dimenPx(R.dimen.elevation_card).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, dimenPx(R.dimen.space_lg))
                }
            }

            menuItems.forEachIndexed { index, spec ->
                val row = createMenuItemRow(
                    label = spec.label,
                    subtitle = spec.subtitle,
                    iconRes = spec.iconRes,
                    onClick = { openOrganizerPage(spec.target, event.id, event.title, viewOnly = spec.id == R.id.emh_menu_edit && !canEdit) },
                ).apply {
                    id = spec.id
                }
                menuCardGroup.addView(row)

                if (index < menuItems.lastIndex) {
                    menuCardGroup.addView(View(this@EventManagementHubActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dimenPx(R.dimen.stroke_hairline),
                        )
                        setBackgroundColor(color(R.color.surface_muted))
                    })
                }
            }
            content.addView(menuCardGroup)
        }
    }

    private data class MenuSpec(
        val label: String,
        val subtitle: String,
        val iconRes: Int,
        val target: Class<*>,
        val id: Int,
    )

    private fun createStatCard(title: String, value: String, iconRes: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_md))
            background = roundedRect(color(R.color.surface), R.dimen.radius_md, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_card).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            // Left icon container
            addView(ImageView(this@EventManagementHubActivity).apply {
                setImageResource(iconRes)
                setColorFilter(color(R.color.deco_icon_on_accent))
                background = roundedRect(color(R.color.accent_container), R.dimen.radius_20)
                setPadding(dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_sm))
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_tile_sm), dimenPx(R.dimen.icon_tile_sm))
            })

            // Right text container
            addView(LinearLayout(this@EventManagementHubActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dimenPx(R.dimen.space_sm)
                }
                addView(styledText(value, R.dimen.type_title, true, color(R.color.text_primary)))
                addView(styledText(title, R.dimen.type_overline, false, color(R.color.text_secondary)))
            })
        }

    private fun createMenuItemRow(
        label: String,
        subtitle: String,
        iconRes: Int,
        onClick: () -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
        setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        // Left Icon Container
        addView(ImageView(this@EventManagementHubActivity).apply {
            setImageResource(iconRes)
            setColorFilter(color(R.color.deco_icon_on_accent))
            background = roundedRect(color(R.color.accent_container), R.dimen.radius_md)
            setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_10))
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44))
        })

        // Middle Text Container
        addView(LinearLayout(this@EventManagementHubActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dimenPx(R.dimen.space_14), 0, dimenPx(R.dimen.space_sm), 0)
            }
            addView(styledText(label, R.dimen.type_body, true, color(R.color.text_primary)))
            addView(styledText(subtitle, R.dimen.type_caption, false, color(R.color.text_secondary)).apply {
                setPadding(0, dimenPx(R.dimen.space_xxs), 0, 0)
            })
        })

        // Right Chevron
        addView(ImageView(this@EventManagementHubActivity).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(color(R.color.neutral))
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
        })
    }
}

