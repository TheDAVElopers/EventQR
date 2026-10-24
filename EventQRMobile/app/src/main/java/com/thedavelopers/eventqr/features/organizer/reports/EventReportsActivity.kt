package com.thedavelopers.eventqr.features.organizer.reports

import com.thedavelopers.eventqr.ui.theme.mediumWeight
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenFloat
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.appcompat.app.AlertDialog
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportCatalogItem
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

open class EventReportsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    @VisibleForTesting
    internal lateinit var reportsRepository: OrganizerReportsRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var content: LinearLayout
    private var requestedEventId: String? = null
    private var loadedEvents: List<OrganizerMvpEvent> = emptyList()
    private var summary: EventReportSummaryDto = EventReportSummaryDto()
    private var skeletonLoading: View? = null
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        reportsRepository = OrganizerReportsRepository(this)

        content = organizerShell(
            title = getString(R.string.event_reports_screen_title),
            selectedNav = NAV_REPORTS,
        )

        requestedEventId = intentEventId() ?: selectedEventId().takeIf { it.isNotBlank() }
        loadEvents()
    }

    private fun loadEvents() {
        content.removeAllViews()
        skeletonLoading = reportsSkeleton()
        content.addView(skeletonLoading)

        lifecycleScope.launch { onEventsLoaded(repository.loadApprovedOrganizerEvents()) }
    }

    @VisibleForTesting
    internal fun onEventsLoaded(eventsLoad: OrganizerMvpLoad<List<OrganizerMvpEvent>>) {
        // A failed fetch is not "no events": show the error with Retry instead of the empty state.
        if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
            content.removeAllViews()
            content.addView(eventsLoadErrorState(eventsLoad.message) { loadEvents() })
            return
        }
        loadedEvents = eventsLoad.data
        val resolvedEvent = resolveSelectedEvent(loadedEvents, requestedEventId)
        if (resolvedEvent != null) {
            selectedEvent = resolvedEvent
            loadScreen()
        } else {
            content.removeAllViews()
            content.addView(centeredEmptyState(
                iconRes = R.drawable.ic_organizer_reports,
                title = getString(R.string.event_reports_no_events_title),
                subtext = getString(R.string.event_reports_no_events_subtext),
            ))
        }
    }

    private fun loadScreen() {
        content.removeAllViews()

        val summaryContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(summaryContainer)
        skeletonLoading = reportsSkeleton()
        summaryContainer.addView(skeletonLoading)

        lifecycleScope.launch {
            when (val result = reportsRepository.fetchSummary(selectedEvent.id)) {
                is NetworkResult.Success -> {
                    summary = result.data
                    renderList(summaryContainer)
                }

                is NetworkResult.Error -> {
                    renderList(summaryContainer)
                    Toast.makeText(
                        this@EventReportsActivity,
                        result.message,
                        Toast.LENGTH_LONG,
                    ).show()
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderList(container: LinearLayout) {
        // The selector reuses the list already loaded by onEventsLoaded; a second fetch could fail on its own
        // and leave the selector empty while the rest of the screen works.
        skeletonLoading?.visibility = View.GONE
        container.removeAllViews()
        container.addView(buildSelectEventCard(loadedEvents))
        container.addView(buildSummaryHeaderCard())
        container.addView(sectionHeader(getString(R.string.event_reports_generate_reports)))
        container.addView(buildReportCatalogContainer(reportCatalog()))
    }

    private fun buildSelectEventCard(events: List<OrganizerMvpEvent>): LinearLayout {
        val approvedEvents = events.approvedOnly()
        val titles = approvedEvents.map { it.title.ifBlank { getString(R.string.common_untitled_event) } }
        var selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEvent.id }.takeIf { it >= 0 } ?: 0
        if (approvedEvents.isEmpty()) selectedIndex = -1

        val currentTitleText = styledText(titles.getOrNull(selectedIndex) ?: getString(R.string.event_reports_select_event), R.dimen.type_body, true, orgColors.text)
        val arrow = buildSelectEventArrow()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md))
            background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dimenPx(R.dimen.space_xs), 0, dimenPx(R.dimen.space_14)) }
        }

        card.addView(buildSelectEventIcon())

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dimenPx(R.dimen.space_md)
                marginEnd = dimenPx(R.dimen.space_sm)
            }
            addView(styledText(getString(R.string.event_reports_select_event_caps), R.dimen.type_micro, true, color(R.color.text_label)))
            addView(currentTitleText)
        }
        card.addView(textLayout)
        card.addView(arrow)

        var popup: PopupWindow? = null
        var isOpen = false

        fun buildDropdown(): LinearLayout = LinearLayout(this@EventReportsActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(color(R.color.surface), R.dimen.radius_14, color(R.color.outline))
            approvedEvents.forEachIndexed { index, event ->
                val selected = index == selectedIndex
                addView(LinearLayout(this@EventReportsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14))
                    setBackgroundColor(if (selected) color(R.color.accent_container) else color(R.color.surface))
                    setOnClickListener {
                        selectedIndex = index
                        currentTitleText.text = titles[index]
                        popup?.dismiss()
                        isOpen = false
                        arrow.rotation = 0f
                        selectedEvent = event
                        repository.saveSelectedEventId(event.id)
                        saveSelectedEventId(event.id)
                        loadScreen()
                    }
                    addView(TextView(this@EventReportsActivity).apply {
                        text = titles[index]
                        setTextColor(if (selected) color(R.color.primary) else orgColors.text)
                        setTextSizeRes(R.dimen.type_body)
                        strong()
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                })
            }
        }

        card.setOnClickListener {
            if (approvedEvents.isEmpty()) return@setOnClickListener
            if (isOpen) {
                popup?.dismiss()
                isOpen = false
                arrow.rotation = 0f
            } else {
                popup?.dismiss()
                popup = PopupWindow(
                    buildDropdown(),
                    card.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true,
                ).apply {
                    isOutsideTouchable = true
                    elevation = dimenPx(R.dimen.elevation_overlay).toFloat()
                    setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
                    setOnDismissListener { isOpen = false; arrow.rotation = 0f }
                }
                isOpen = true
                arrow.rotation = 180f
                popup.showAsDropDown(card, 0, dimenPx(R.dimen.space_xs))
            }
        }

        return card
    }

    // Shared by the loaded and skeleton select-event cards so the constant icon/arrow can't drift.
    private fun buildSelectEventIcon(): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = roundedRect(color(R.color.accent_container), R.dimen.radius_md)
        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44))
        addView(ImageView(this@EventReportsActivity).apply {
            setImageResource(R.drawable.ic_calendar)
            setColorFilter(color(R.color.chart_5))
            layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_22), dimenPx(R.dimen.icon_22))
        })
    }

    private fun buildSelectEventArrow(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.ic_arrow_drop_down)
        setColorFilter(color(R.color.neutral))
        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
    }

    // Same gradient as the organizer dashboard header, with the card's corner radius.
    private fun dashboardGradient(): GradientDrawable =
        (ResourcesCompat.getDrawable(resources, R.drawable.bg_dashboard_gradient, theme)!!.mutate() as GradientDrawable).apply {
            cornerRadius = dimenPx(R.dimen.radius_lg).toFloat()
        }

    private fun buildSummaryHeaderCard(): LinearLayout {
        val gradient = dashboardGradient()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_18))
            elevation = dimenPx(R.dimen.elevation_lifted).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_18)) }

            val topRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(TextView(this@EventReportsActivity).apply {
                    text = selectedEvent.title
                    setTextSizeRes(R.dimen.type_subtitle)
                    strong()
                    setTextColor(color(R.color.on_primary))
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dimenPx(R.dimen.space_sm)
                    }
                })

                addView(LinearLayout(this@EventReportsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedRect(color(R.color.on_dark_15), R.dimen.radius_md)
                    setPadding(dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_xs))

                    addView(View(this@EventReportsActivity).apply {
                        background = roundedRect(ResourcesCompat.getColor(resources, R.color.success, theme), R.dimen.radius_3)
                        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.progress_bar_height), dimenPx(R.dimen.progress_bar_height)).apply {
                            marginEnd = dimenPx(R.dimen.space_5)
                        }
                    })

                    addView(TextView(this@EventReportsActivity).apply {
                        text = getString(R.string.event_reports_selected_event)
                        setTextSizeRes(R.dimen.type_overline)
                        strong()
                        setTextColor(color(R.color.accent_container_strong))
                    })
                })
            }
            addView(topRow)

            val statsRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dimenPx(R.dimen.space_lg), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(summaryStatColumn(getString(R.string.common_registered), summary.registeredCount, first = true))

                addView(summaryStatColumn(getString(R.string.common_checked_in), summary.checkedInCount))

                addView(summaryStatColumn(getString(R.string.event_reports_exited), summary.exitedCount))
            }
            addView(statsRow)
        }
    }

    // Mirrors the attendee dashboard stat tiles: translucent tile, 12sp medium label (on_dark_50)
    // above a 22sp bold white value. The card is already the dark dashboard gradient, so colours carry over.
    private fun summaryStatColumn(label: String, value: Int, first: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_dashboard_stat_card, theme)
        setPadding(0, dimenPx(R.dimen.space_14), 0, dimenPx(R.dimen.space_14))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (!first) marginStart = dimenPx(R.dimen.space_sm)
        }
        addView(TextView(this@EventReportsActivity).apply {
            text = label
            setTextSizeRes(R.dimen.type_caption)
            mediumWeight()
            setTextColor(color(R.color.on_dark_50))
            gravity = Gravity.CENTER
        })
        addView(TextView(this@EventReportsActivity).apply {
            text = formatCount(value)
            setTextSizeRes(R.dimen.type_headline)
            strong()
            setTextColor(color(R.color.on_primary))
            gravity = Gravity.CENTER
            setPadding(0, dimenPx(R.dimen.space_xs), 0, 0)
        })
    }

    private fun sectionHeader(label: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_xs), dimenPx(R.dimen.space_xxs), dimenPx(R.dimen.space_md))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        addView(styledText(label, R.dimen.type_title_large, true, color(R.color.primary_dark)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        val actionRow = LinearLayout(this@EventReportsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { generateAllReports() }

            addView(TextView(this@EventReportsActivity).apply {
                text = getString(R.string.event_reports_generate_all)
                setTextSizeRes(R.dimen.type_body_medium)
                strong()
                setTextColor(color(R.color.primary))
            })

            addView(ImageView(this@EventReportsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(color(R.color.primary))
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_18), dimenPx(R.dimen.icon_18)).apply {
                    marginStart = dimenPx(R.dimen.space_xxs)
                }
            })
        }
        addView(actionRow)
    }

    private fun buildReportCatalogContainer(items: List<EventReportCatalogItem>): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_xl)) }
        }

        items.forEachIndexed { index, item ->
            val itemView = buildReportMenuItem(item) { openFilterSheet(item) }
            container.addView(itemView)

            if (index < items.size - 1) {
                container.addView(View(this).apply {
                    setBackgroundColor(color(R.color.surface_muted))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dimenPx(R.dimen.stroke_hairline),
                    ).apply { setMargins(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_lg), 0) }
                })
            }
        }

        return container
    }

    private fun buildReportMenuItem(
        item: EventReportCatalogItem,
        onClick: () -> Unit,
    ): LinearLayout {
        val subtitle = getReportSubtitle(item.reportType)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val outVal = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
            setBackgroundResource(outVal.resourceId)

            addView(LinearLayout(this@EventReportsActivity).apply {
                gravity = Gravity.CENTER
                background = roundedRect(item.iconBg, R.dimen.radius_md)
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44))
                addView(ImageView(this@EventReportsActivity).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(item.iconTint)
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_22), dimenPx(R.dimen.icon_22))
                })
            })

            addView(LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dimenPx(R.dimen.space_14)
                    marginEnd = dimenPx(R.dimen.space_sm)
                }
                addView(TextView(this@EventReportsActivity).apply {
                    text = item.label
                    setTextSizeRes(R.dimen.type_body)
                    strong()
                    setTextColor(color(R.color.primary_dark))
                })
                if (subtitle.isNotBlank()) {
                    addView(TextView(this@EventReportsActivity).apply {
                        text = subtitle
                        setTextSizeRes(R.dimen.type_caption)
                        setTextColor(color(R.color.text_secondary))
                        setPadding(0, dimenPx(R.dimen.space_xxs), 0, 0)
                    })
                }
            })

            addView(ImageView(this@EventReportsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(color(R.color.neutral))
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
            })
        }
    }

    private fun getReportSubtitle(type: EventReportType): String = when (type) {
        EventReportType.ROSTER -> getString(R.string.event_reports_subtitle_roster)
        EventReportType.NO_SHOWS -> getString(R.string.event_reports_subtitle_no_shows)
        EventReportType.ENTRY_LOGS -> getString(R.string.event_reports_subtitle_entry_logs)
        EventReportType.ATTENDANCE -> getString(R.string.event_reports_subtitle_attendance)
        EventReportType.CLAIMS -> getString(R.string.event_reports_subtitle_claims)
        EventReportType.BOOTH_VISITS -> getString(R.string.event_reports_subtitle_booth_visits)
        EventReportType.EXIT_LOGS -> getString(R.string.event_reports_subtitle_exit_logs)
        EventReportType.POINTS -> getString(R.string.event_reports_subtitle_points)
    }

    private fun reportsSkeleton(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        addView(buildSelectEventSkeletonCard())
        addView(buildSummarySkeletonCard())
        addView(sectionHeader(getString(R.string.event_reports_generate_reports)))
        addView(buildReportCatalogSkeletonContainer())
    }

    private fun buildSelectEventSkeletonCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_md))
            background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dimenPx(R.dimen.space_xs), 0, dimenPx(R.dimen.space_14)) }
        }

        card.addView(buildSelectEventIcon())

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dimenPx(R.dimen.space_md)
                marginEnd = dimenPx(R.dimen.space_sm)
            }
            addView(styledText(getString(R.string.event_reports_select_event_caps), R.dimen.type_micro, true, color(R.color.text_label)))
            addView(View(this@EventReportsActivity).apply {
                background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.size_120), dimenPx(R.dimen.icon_sm)).apply {
                    topMargin = dimenPx(R.dimen.space_xs)
                }
            })
        }
        card.addView(textLayout)

        card.addView(buildSelectEventArrow())

        return card
    }

    private fun buildSummarySkeletonCard(): LinearLayout {
        val gradient = dashboardGradient()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient
            setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_18))
            elevation = dimenPx(R.dimen.elevation_lifted).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_18)) }

            val topRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(summarySkeletonBlock(0, R.dimen.icon_18, R.dimen.radius_sm).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dimenPx(R.dimen.icon_18), 1f).apply { marginEnd = dimenPx(R.dimen.space_sm) }
                })
                addView(summarySkeletonBlock(dimenPx(R.dimen.tile_80), R.dimen.icon_22, R.dimen.radius_md))
            }
            addView(topRow)

            val statsRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dimenPx(R.dimen.space_lg), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                repeat(3) { i -> addView(summaryStatSkeletonColumn(first = i == 0)) }
            }
            addView(statsRow)
        }
    }

    // Translucent white placeholder block for use on the purple gradient (never opaque).
    private fun summarySkeletonBlock(widthPx: Int, @DimenRes heightRes: Int, @DimenRes radiusRes: Int): View = View(this).apply {
        background = roundedRect(color(R.color.on_dark_20), radiusRes)
        layoutParams = LinearLayout.LayoutParams(widthPx, dimenPx(heightRes))
    }

    // Same tile geometry as summaryStatColumn (14dp padding, 12sp label + 22sp value) so there is no layout jump.
    private fun summaryStatSkeletonColumn(first: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_dashboard_stat_card, theme)
        setPadding(0, dimenPx(R.dimen.space_14), 0, dimenPx(R.dimen.space_14))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (!first) marginStart = dimenPx(R.dimen.space_sm)
        }
        addView(summarySkeletonBlock(dimenPx(R.dimen.touch_target_min), R.dimen.space_md, R.dimen.radius_6))
        addView(summarySkeletonBlock(dimenPx(R.dimen.icon_32), R.dimen.icon_22, R.dimen.radius_6).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dimenPx(R.dimen.space_sm)
        })
    }

    private fun buildReportCatalogSkeletonContainer(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(color(R.color.surface), R.dimen.radius_lg, color(R.color.outline))
            elevation = dimenPx(R.dimen.elevation_raised).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_xl)) }
        }

        repeat(5) { index ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_14))

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.tile_44), dimenPx(R.dimen.tile_44))
                })

                val textLayout = LinearLayout(this@EventReportsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dimenPx(R.dimen.space_14)
                        marginEnd = dimenPx(R.dimen.space_sm)
                    }
                    addView(View(this@EventReportsActivity).apply {
                        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.size_140), dimenPx(R.dimen.icon_sm))
                    })
                    addView(View(this@EventReportsActivity).apply {
                        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.size_200), dimenPx(R.dimen.space_md)).apply {
                            topMargin = dimenPx(R.dimen.space_6)
                        }
                    })
                }
                addView(textLayout)

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
                })
            }
            container.addView(rowView)

            if (index < 4) {
                container.addView(View(this).apply {
                    setBackgroundColor(color(R.color.surface_muted))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dimenPx(R.dimen.stroke_hairline),
                    ).apply { setMargins(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_lg), 0) }
                })
            }
        }

        return container


    }

    private fun openFilterSheet(item: EventReportCatalogItem) {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_20), dimenPx(R.dimen.space_20))
            setBackgroundColor(color(R.color.surface))
        }

        root.addView(styledText(item.label, R.dimen.type_title, true))
        root.addView(styledText(getString(R.string.event_reports_optional_filters), R.dimen.type_body_compact, false, orgColors.muted).apply {
            setPadding(0, dimenPx(R.dimen.space_xs), 0, dimenPx(R.dimen.space_md))
        })

        var startDate: LocalDate? = null
        var endDate: LocalDate? = null
        var status: EventReportFilterStatus = EventReportFilterStatus.ALL

        val dateError = styledText("", R.dimen.type_caption, false, orgColors.error).apply { visibility = View.GONE }
        val dateHint = styledText(getString(R.string.event_reports_dates_optional_hint), R.dimen.type_caption, false, orgColors.muted)

        lateinit var generateButton: Button
        // Every filter is optional: no dates, only a start, only an end or both. Only end < start blocks Generate.
        fun refreshGenerateState() {
            val ready = ReportFilterRules.canGenerate(startDate, endDate)
            generateButton.isEnabled = ready
            generateButton.alpha = if (ready) 1f else dimenFloat(R.dimen.alpha_busy)
            if (ready) {
                dateError.visibility = View.GONE
            } else {
                dateError.text = getString(R.string.event_reports_end_date_must_be_after_start_date)
                dateError.visibility = View.VISIBLE
            }
        }

        val startDateInput = buildDateInput(getString(R.string.event_reports_start_date)) { picked ->
            startDate = picked
            refreshGenerateState()
        }
        val endDateInput = buildDateInput(getString(R.string.event_reports_end_date)) { picked ->
            endDate = picked
            refreshGenerateState()
        }

        root.addView(startDateInput.wrapper)
        root.addView(endDateInput.wrapper)
        root.addView(dateError)

        var attendeeQuery = ""
        if (OrganizerReportsRepository.attendeeQueryApplicable.contains(item.reportType)) {
            root.addView(labeledSearchInput(getString(R.string.event_reports_attendee_search_label)) { attendeeQuery = it })
        }

        if (OrganizerReportsRepository.transactionStatusApplicable.contains(item.reportType)) {
            root.addView(statusSelector { status = it })
        }

        generateButton = primaryButton(getString(R.string.event_reports_generate)) {
            if (!ReportFilterRules.canGenerate(startDate, endDate)) {
                refreshGenerateState()
                return@primaryButton
            }

            val filters = EventReportFiltersDto(
                startDate = startDate,
                endDate = endDate,
                attendeeQuery = attendeeQuery.takeIf { it.isNotBlank() },
                status = status,
            )
            generateSingleReport(item, filters, dialog, root)
        }

        // Clears only the DATE filters; the attendee search and status chips stay applied.
        val skipButton = ghostButton(getString(R.string.event_reports_skip_date_filters)) {
            val current = EventReportFiltersDto(
                startDate = startDate,
                endDate = endDate,
                attendeeQuery = attendeeQuery.takeIf { it.isNotBlank() },
                status = status,
            )
            generateSingleReport(item, ReportFilterRules.clearDates(current), dialog, root)
        }

        root.addView(dateHint.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dimenPx(R.dimen.space_sm), 0, 0)
            }
        })
        root.addView(generateButton.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.touch_target_min)).apply {
                setMargins(0, 0, 0, dimenPx(R.dimen.space_sm))
            }
        })
        root.addView(skipButton.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.touch_target_min))
        })

        refreshGenerateState()

        dialog.setContentView(root)
        dialog.show()
    }

    private fun generateSingleReport(
        item: EventReportCatalogItem,
        filters: EventReportFiltersDto,
        dialog: BottomSheetDialog,
        sheetRoot: LinearLayout,
    ) {
        lifecycleScope.launch {
            when (val result = reportsRepository.generateReport(selectedEvent.id, item.reportType, filters)) {
                is NetworkResult.Success -> {
                    dialog.dismiss()
                    startActivity(
                        ReportPreviewActivity.newSingleIntent(
                            context = this@EventReportsActivity,
                            eventId = selectedEvent.id,
                            report = result.data,
                            summary = summary,
                            sourceFilters = filters,
                        ),
                    )
                }

                is NetworkResult.Error -> {
                    Snackbar.make(sheetRoot, result.message, Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.common_retry)) { generateSingleReport(item, filters, dialog, sheetRoot) }
                        .show()
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun generateAllReports() {
        val combined = mutableListOf<EventReportDto>()
        val allTypes = reportCatalog().map { it.reportType }
        val loading = AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_reports_generating_reports))
            .setMessage(getString(R.string.event_reports_please_wait_while_all_report_section))
            .setCancelable(false)
            .create()
        loading.show()

        val job = lifecycleScope.launch {
            for (type in allTypes) {
                when (val result = reportsRepository.generateReport(selectedEvent.id, type, OrganizerReportsRepository.defaultFilters())) {
                    is NetworkResult.Success -> combined.add(result.data)
                    is NetworkResult.Error -> {
                        loading.dismiss()
                        Toast.makeText(this@EventReportsActivity, result.message, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    NetworkResult.Loading -> Unit
                }
            }

            loading.dismiss()
            startActivity(
                ReportPreviewActivity.newCombinedIntent(
                    context = this@EventReportsActivity,
                    eventId = selectedEvent.id,
                    reports = combined,
                    summary = summary,
                ),
            )
        }
        // Leaving mid-generation cancels the job; drop the modal so it does not leak the window.
        job.invokeOnCompletion { loading.dismiss() }
    }

    private fun reportCatalog(): List<EventReportCatalogItem> = listOf(
        EventReportCatalogItem(EventReportType.ROSTER, getString(R.string.event_reports_title_roster), R.drawable.ic_group, color(R.color.chart_5), color(R.color.accent_container)),
        EventReportCatalogItem(EventReportType.NO_SHOWS, getString(R.string.event_reports_title_no_shows), R.drawable.ic_nav_profile, color(R.color.danger), color(R.color.error_container)),
        EventReportCatalogItem(EventReportType.ENTRY_LOGS, getString(R.string.event_reports_title_entry_logs), R.drawable.ic_scan, color(R.color.chart_3), color(R.color.tile_violet)),
        EventReportCatalogItem(EventReportType.ATTENDANCE, getString(R.string.event_reports_title_attendance), R.drawable.ic_organizer_bar_chart, color(R.color.chart_1), color(R.color.accent_container)),
        EventReportCatalogItem(EventReportType.CLAIMS, getString(R.string.event_reports_title_claims), R.drawable.ic_nav_gift, color(R.color.chart_7), color(R.color.warning_container)),
        EventReportCatalogItem(EventReportType.BOOTH_VISITS, getString(R.string.event_reports_title_booth_visits), R.drawable.ic_calendar, color(R.color.chart_6), color(R.color.success_container)),
        EventReportCatalogItem(EventReportType.EXIT_LOGS, getString(R.string.event_reports_title_exit_logs), R.drawable.ic_chevron_right, color(R.color.chart_4), color(R.color.info_container)),
        EventReportCatalogItem(EventReportType.POINTS, getString(R.string.event_reports_title_points), R.drawable.ic_organizer_reports, color(R.color.chart_2), color(R.color.tile_cyan)),
    )

    private data class DateInputHolder(val wrapper: LinearLayout, val valueView: TextView)

    private fun buildDateInput(label: String, onChanged: (LocalDate?) -> Unit): DateInputHolder {
        val valueView = styledText(getString(R.string.event_reports_select), R.dimen.type_body_medium, false, orgColors.muted)
        val wrapper = cardRes(R.dimen.space_md).apply {
            addView(styledText(label, R.dimen.type_body_compact, true))
            addView(row().apply {
                setPadding(0, dimenPx(R.dimen.space_sm), 0, 0)
                addView(valueView.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(ImageView(this@EventReportsActivity).apply {
                    setImageResource(R.drawable.ic_calendar)
                    setColorFilter(orgColors.muted)
                    layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
                })
            })
            setOnClickListener {
                val today = LocalDate.now(ZoneId.of("Asia/Manila"))
                android.app.DatePickerDialog(
                    this@EventReportsActivity,
                    { _, year, month, dayOfMonth ->
                        val picked = LocalDate.of(year, month + 1, dayOfMonth)
                        valueView.text = dateFormatter.format(picked)
                        valueView.setTextColor(orgColors.text)
                        onChanged(picked)
                    },
                    today.year,
                    today.monthValue - 1,
                    today.dayOfMonth,
                ).show()
            }
        }
        return DateInputHolder(wrapper, valueView)
    }

    private fun labeledSearchInput(label: String, onChanged: (String) -> Unit): LinearLayout = cardRes(R.dimen.space_md).apply {
        addView(styledText(label, R.dimen.type_body_compact, true))
        addView(EditText(this@EventReportsActivity).apply {
            hint = getString(R.string.event_reports_attendee_search_hint)
            setPadding(dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10), dimenPx(R.dimen.space_md), dimenPx(R.dimen.space_10))
            background = roundedRect(color(R.color.background), R.dimen.radius_10, orgColors.border)
            setTextColor(orgColors.text)
            setHintTextColor(orgColors.muted)
            afterTextChanged { onChanged(text.toString()) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dimenPx(R.dimen.space_sm), 0, 0)
            }
        })
    }

    private fun statusSelector(onSelected: (EventReportFilterStatus) -> Unit): LinearLayout = cardRes(R.dimen.space_md).apply {
        addView(styledText(getString(R.string.event_reports_transaction_status), R.dimen.type_body_compact, true))
        val chipRow = row().apply {
            setPadding(0, dimenPx(R.dimen.space_10), 0, 0)
            gravity = Gravity.START
        }

        fun statusChip(label: String, status: EventReportFilterStatus): TextView {
            return chip(label, status == EventReportFilterStatus.ALL).apply {
                setOnClickListener {
                    onSelected(status)
                    for (idx in 0 until chipRow.childCount) {
                        val child = chipRow.getChildAt(idx) as? TextView ?: continue
                        val isActive = child.text.toString().equals(label, ignoreCase = true)
                        child.setTextColor(if (isActive) color(R.color.on_primary) else orgColors.primary)
                        child.background = roundedRect(if (isActive) orgColors.primary else color(R.color.surface), R.dimen.radius_18, if (isActive) null else orgColors.border)
                    }
                }
            }
        }

        chipRow.addView(statusChip(getString(R.string.common_all), EventReportFilterStatus.ALL))
        chipRow.addView(statusChip(getString(R.string.common_approved), EventReportFilterStatus.APPROVED))
        chipRow.addView(statusChip(getString(R.string.common_rejected), EventReportFilterStatus.REJECTED))
        addView(chipRow)
    }
}
