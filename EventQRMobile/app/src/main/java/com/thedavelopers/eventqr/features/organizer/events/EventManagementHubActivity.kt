package com.thedavelopers.eventqr.features.organizer.events

import androidx.lifecycle.lifecycleScope
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
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

            content.setPadding(dp(16), dp(12), dp(16), dp(24))

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
                    setMargins(0, 0, 0, dp(16))
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#0F172A"),
                        Color.parseColor("#1E1B4B"),
                        Color.parseColor("#312E81"),
                    ),
                ).apply {
                    cornerRadius = dp(16).toFloat()
                }
                setPadding(dp(20), dp(20), dp(20), dp(20))

                val leftColumn = LinearLayout(this@EventManagementHubActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )

                    // Badge Pill
                    val statusText = event.lifecycleStatus()
                    val badgeColor = Color.parseColor("#ffffff")
                    val badgeBackground = Color.parseColor("#33FFFFFF")
                    val badgeLayout = LinearLayout(this@EventManagementHubActivity).apply {
                        id = R.id.emh_status_badge
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(badgeBackground, 16, null, density = resources.displayMetrics.density)
                        setPadding(dp(10), dp(4), dp(12), dp(4))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )

                        addView(ImageView(this@EventManagementHubActivity).apply {
                            setImageResource(R.drawable.ic_calendar)
                            setColorFilter(badgeColor)
                            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                                marginEnd = dp(6)
                            }
                        })

                        addView(text(statusText, 11, true, badgeColor))
                    }
                    addView(badgeLayout)

                    // Event Title
                    addView(text(event.title, 20, true, Color.WHITE).apply {
                        id = R.id.emh_event_title
                        setPadding(0, dp(10), 0, 0)
                    })

                    // Subtitle / Description
                    val descText = event.description.ifBlank {
                        getString(R.string.event_management_hub_default_description)
                    }
                    addView(text(descText, 12, false, Color.argb(220, 255, 255, 255)).apply {
                        setPadding(0, dp(4), 0, 0)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
                addView(leftColumn)

                // Large White Icon on Right
                addView(ImageView(this@EventManagementHubActivity).apply {
                    setImageResource(R.drawable.ic_calendar)
                    setColorFilter(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                        marginStart = dp(12)
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
                    setMargins(0, 0, 0, dp(16))
                }

                val stat1 = createStatCard(getString(R.string.event_management_hub_stat_registered), formatCount(registeredCount), R.drawable.ic_profile_person).apply {
                    id = R.id.emh_stat_registered
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginEnd = dp(4) }
                }
                val stat2 = createStatCard(getString(R.string.event_management_hub_stat_capacity), formatCount(capacity), R.drawable.ic_group).apply {
                    id = R.id.emh_stat_capacity
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginStart = dp(4); marginEnd = dp(4) }
                }
                val stat3 = createStatCard(getString(R.string.event_management_hub_stat_available), formatCount(available), R.drawable.ic_row_star).apply {
                    id = R.id.emh_stat_available
                    layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { marginStart = dp(4) }
                }

                addView(stat1)
                addView(stat2)
                addView(stat3)
            }
            content.addView(statsRow)

            // Section Title
            content.addView(text(getString(R.string.event_management_hub_section_title), 16, true, Color.parseColor("#111827")).apply {
                id = R.id.emh_section_title
                setPadding(dp(2), dp(4), dp(2), dp(12))
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
                background = rounded(Color.WHITE, 12, Color.parseColor("#E2E8F0"), density = resources.displayMetrics.density)
                elevation = dp(1).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, dp(16))
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
                            dp(1),
                        )
                        setBackgroundColor(Color.parseColor("#F1F5F9"))
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
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = rounded(Color.WHITE, 12, Color.parseColor("#E2E8F0"), density = resources.displayMetrics.density)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            // Left icon container
            addView(ImageView(this@EventManagementHubActivity).apply {
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#5A45F2"))
                background = rounded(Color.parseColor("#EEF0FF"), 20, null, density = resources.displayMetrics.density)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            })

            // Right text container
            addView(LinearLayout(this@EventManagementHubActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
                addView(text(value, 18, true, Color.parseColor("#111827")))
                addView(text(title, 11, false, Color.parseColor("#6B7280")))
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
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        // Left Icon Container
        addView(ImageView(this@EventManagementHubActivity).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#5A45F2"))
            background = rounded(Color.parseColor("#EEF0FF"), 12, null, density = resources.displayMetrics.density)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })

        // Middle Text Container
        addView(LinearLayout(this@EventManagementHubActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(14), 0, dp(8), 0)
            }
            addView(text(label, 15, true, Color.parseColor("#111827")))
            addView(text(subtitle, 12, false, Color.parseColor("#64748B")).apply {
                setPadding(0, dp(2), 0, 0)
            })
        })

        // Right Chevron
        addView(ImageView(this@EventManagementHubActivity).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })
    }
}

