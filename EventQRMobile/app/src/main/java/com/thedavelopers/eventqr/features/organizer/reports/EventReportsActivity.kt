package com.thedavelopers.eventqr.features.organizer.reports

import android.graphics.Color
import android.graphics.Typeface
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
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

open class EventReportsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var reportsRepository: OrganizerReportsRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var content: LinearLayout
    private var summary: EventReportSummaryDto = EventReportSummaryDto()
    private var skeletonLoading: View? = null
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        reportsRepository = OrganizerReportsRepository(this)

        content = organizerShell(
            title = "Event Reports",
            selectedNav = NAV_REPORTS,
        )

        val eventId = intentEventId() ?: selectedEventId().takeIf { it.isNotBlank() }
        skeletonLoading = reportsSkeleton()
        content.addView(skeletonLoading)

        lifecycleScope.launch {
            val events = repository.getApprovedOrganizerEvents()
            val resolvedEvent = if (eventId != null) resolveSelectedEvent(events, eventId) else null

            if (resolvedEvent != null) {
                selectedEvent = resolvedEvent
                loadScreen()
            } else {
                content.removeAllViews()
                content.addView(centeredEmptyState(
                    iconRes = R.drawable.ic_organizer_reports,
                    title = "No Events Available",
                    subtext = "Create an event in the Events tab to view and generate reports.",
                ))
            }
        }
    }

    private fun loadScreen() {
        content.removeAllViews()

        val summaryContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(summaryContainer)
        skeletonLoading = reportsSkeleton()
        summaryContainer.addView(skeletonLoading)

        MainScope().launch {
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
        skeletonLoading?.visibility = View.GONE
        container.removeAllViews()

        lifecycleScope.launch {
            val events = repository.getApprovedOrganizerEvents()
            container.addView(buildSelectEventCard(events))
            container.addView(buildSummaryHeaderCard())
            container.addView(sectionHeader("Generate Reports"))
            container.addView(buildReportCatalogContainer(reportCatalog()))
        }
    }

    private fun buildSelectEventCard(events: List<OrganizerMvpEvent>): LinearLayout {
        val approvedEvents = events.approvedOnly()
        val titles = approvedEvents.map { it.title.ifBlank { "Untitled Event" } }
        var selectedIndex = approvedEvents.indexOfFirst { it.id == selectedEvent.id }.takeIf { it >= 0 } ?: 0
        if (approvedEvents.isEmpty()) selectedIndex = -1

        val currentTitleText = text(titles.getOrNull(selectedIndex) ?: "Select Event", 15, true, TEXT)
        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_drop_down)
            setColorFilter(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.WHITE, 16, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(14)) }
        }

        val iconContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#EEF2FF"), 12, null, density = resources.displayMetrics.density)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            addView(ImageView(this@EventReportsActivity).apply {
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(Color.parseColor("#6366F1"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
        }
        card.addView(iconContainer)

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
            addView(text("SELECT EVENT", 10, true, Color.parseColor("#8E8EA9")))
            addView(currentTitleText)
        }
        card.addView(textLayout)
        card.addView(arrow)

        var popup: PopupWindow? = null
        var isOpen = false

        fun buildDropdown(): LinearLayout = LinearLayout(this@EventReportsActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 14, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            approvedEvents.forEachIndexed { index, event ->
                val selected = index == selectedIndex
                addView(LinearLayout(this@EventReportsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    setBackgroundColor(if (selected) Color.parseColor("#EEF2FF") else Color.WHITE)
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
                        setTextColor(if (selected) Color.parseColor("#4F46E5") else TEXT)
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
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
                    elevation = dp(8).toFloat()
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    setOnDismissListener { isOpen = false; arrow.rotation = 0f }
                }
                isOpen = true
                arrow.rotation = 180f
                popup.showAsDropDown(card, 0, dp(4))
            }
        }

        return card
    }

    private fun buildSummaryHeaderCard(): LinearLayout {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#272262"), Color.parseColor("#4F46E5")),
        ).apply {
            cornerRadius = dp(16).toFloat()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient
            setPadding(dp(16), dp(16), dp(16), dp(18))
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(18)) }

            val topRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(LinearLayout(this@EventReportsActivity).apply {
                    gravity = Gravity.CENTER
                    background = rounded(Color.parseColor("#33FFFFFF"), 10, null, density = resources.displayMetrics.density)
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                    addView(ImageView(this@EventReportsActivity).apply {
                        setImageResource(R.drawable.ic_organizer_reports)
                        setColorFilter(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                    })
                })

                addView(TextView(this@EventReportsActivity).apply {
                    text = selectedEvent.title
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(12)
                        marginEnd = dp(8)
                    }
                })

                addView(LinearLayout(this@EventReportsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(Color.parseColor("#25FFFFFF"), 12, null, density = resources.displayMetrics.density)
                    setPadding(dp(10), dp(4), dp(10), dp(4))

                    addView(View(this@EventReportsActivity).apply {
                        background = rounded(Color.parseColor("#22D3EE"), 3, null, density = resources.displayMetrics.density)
                        layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                            marginEnd = dp(5)
                        }
                    })

                    addView(TextView(this@EventReportsActivity).apply {
                        text = "Selected Event"
                        textSize = 11f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#E0E7FF"))
                    })
                })
            }
            addView(topRow)

            val statsRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(summaryStatColumn("Registered", summary.registeredCount))

                addView(View(this@EventReportsActivity).apply {
                    setBackgroundColor(Color.parseColor("#30FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(36))
                })

                addView(summaryStatColumn("Checked In", summary.checkedInCount))

                addView(View(this@EventReportsActivity).apply {
                    setBackgroundColor(Color.parseColor("#30FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(36))
                })

                addView(summaryStatColumn("Exited", summary.exitedCount))
            }
            addView(statsRow)
        }
    }

    private fun summaryStatColumn(label: String, value: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(text(formatCount(value), 26, true, Color.parseColor("#38BDF8")).apply {
            gravity = Gravity.CENTER
        })
        addView(text(label, 12, false, Color.parseColor("#CDD6F5")).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
    }

    private fun sectionHeader(label: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(2), dp(4), dp(2), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        addView(text(label, 20, true, Color.parseColor("#1E1B4B")).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        val actionRow = LinearLayout(this@EventReportsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { generateAllReports() }

            addView(TextView(this@EventReportsActivity).apply {
                text = "Generate All"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#4F46E5"))
            })

            addView(ImageView(this@EventReportsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(Color.parseColor("#4F46E5"))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    marginStart = dp(2)
                }
            })
        }
        addView(actionRow)
    }

    private fun buildReportCatalogContainer(items: List<EventReportCatalogItem>): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 16, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(24)) }
        }

        items.forEachIndexed { index, item ->
            val itemView = buildReportMenuItem(item) { openFilterSheet(item) }
            container.addView(itemView)

            if (index < items.size - 1) {
                container.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor("#F3F4F6"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1),
                    ).apply { setMargins(dp(16), 0, dp(16), 0) }
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
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val outVal = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)
            setBackgroundResource(outVal.resourceId)

            addView(LinearLayout(this@EventReportsActivity).apply {
                gravity = Gravity.CENTER
                background = rounded(item.iconBg, 12, null, density = resources.displayMetrics.density)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                addView(ImageView(this@EventReportsActivity).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(item.iconTint)
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                })
            })

            addView(LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(14)
                    marginEnd = dp(8)
                }
                addView(TextView(this@EventReportsActivity).apply {
                    text = item.label
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E1B4B"))
                })
                if (subtitle.isNotBlank()) {
                    addView(TextView(this@EventReportsActivity).apply {
                        text = subtitle
                        textSize = 12f
                        setTextColor(Color.parseColor("#6B7280"))
                        setPadding(0, dp(2), 0, 0)
                    })
                }
            })

            addView(ImageView(this@EventReportsActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(Color.parseColor("#9CA3AF"))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
        }
    }

    private fun getReportSubtitle(type: EventReportType): String = when (type) {
        EventReportType.ROSTER -> "View the list of all registered attendees."
        EventReportType.NO_SHOWS -> "View attendees who have not checked in yet."
        EventReportType.ENTRY_LOGS -> "View entry logs and scan history."
        EventReportType.ATTENDANCE -> "View attendance summary and statistics."
        EventReportType.CLAIMS -> "View benefit claims and redemption details."
        EventReportType.BOOTH_VISITS -> "View booth and session visit details."
        EventReportType.EXIT_LOGS -> "View exit logs and departure history."
        EventReportType.POINTS -> "View attendee points and transactions."
    }

    private fun reportsSkeleton(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        addView(buildSelectEventSkeletonCard())
        addView(buildSummarySkeletonCard())
        addView(sectionHeader("Generate Reports"))
        addView(buildReportCatalogSkeletonContainer())
    }

    private fun buildSelectEventSkeletonCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.WHITE, 16, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(14)) }
        }

        card.addView(View(this).apply {
            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
            addView(text("SELECT EVENT", 10, true, Color.parseColor("#8E8EA9")))
            addView(View(this@EventReportsActivity).apply {
                background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(16)).apply {
                    topMargin = dp(4)
                }
            })
        }
        card.addView(textLayout)

        card.addView(View(this).apply {
            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        })

        return card
    }

    private fun buildSummarySkeletonCard(): LinearLayout {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#272262"), Color.parseColor("#4F46E5")),
        ).apply {
            cornerRadius = dp(16).toFloat()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient
            setPadding(dp(16), dp(16), dp(16), dp(18))
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(18)) }

            val topRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                })

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                    layoutParams = LinearLayout.LayoutParams(0, dp(20), 1f).apply {
                        marginStart = dp(12)
                        marginEnd = dp(8)
                    }
                })

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                    layoutParams = LinearLayout.LayoutParams(dp(80), dp(22))
                })
            }
            addView(topRow)

            val statsRow = LinearLayout(this@EventReportsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                addView(summaryStatColumn("Registered", 0))
                addView(View(this@EventReportsActivity).apply {
                    setBackgroundColor(Color.parseColor("#30FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(36))
                })
                addView(summaryStatColumn("Checked In", 0))
                addView(View(this@EventReportsActivity).apply {
                    setBackgroundColor(Color.parseColor("#30FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(36))
                })
                addView(summaryStatColumn("Exited", 0))
            }
            addView(statsRow)
        }
    }

    private fun buildReportCatalogSkeletonContainer(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 16, Color.parseColor("#E5E7EB"), density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(24)) }
        }

        repeat(5) { index ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                })

                val textLayout = LinearLayout(this@EventReportsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(14)
                        marginEnd = dp(8)
                    }
                    addView(View(this@EventReportsActivity).apply {
                        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                        layoutParams = LinearLayout.LayoutParams(dp(140), dp(16))
                    })
                    addView(View(this@EventReportsActivity).apply {
                        background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_bar, theme)
                        layoutParams = LinearLayout.LayoutParams(dp(200), dp(12)).apply {
                            topMargin = dp(6)
                        }
                    })
                }
                addView(textLayout)

                addView(View(this@EventReportsActivity).apply {
                    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_staff_skeleton_date_block, theme)
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                })
            }
            container.addView(rowView)

            if (index < 4) {
                container.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor("#F3F4F6"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1),
                    ).apply { setMargins(dp(16), 0, dp(16), 0) }
                })
            }
        }

        return container


    }

    private fun openFilterSheet(item: EventReportCatalogItem) {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(text(item.label, 18, true))
        root.addView(text("Optional filters", 13, false, MUTED).apply {
            setPadding(0, dp(4), 0, dp(12))
        })

        var startDate: LocalDate? = null
        var endDate: LocalDate? = null
        var status: EventReportFilterStatus = EventReportFilterStatus.ALL

        val dateError = text("", 12, false, ERROR).apply { visibility = View.GONE }
        val dateHint = text("Select both Start Date and End Date to generate.", 12, false, MUTED)
            .apply { visibility = View.GONE }

        lateinit var generateButton: Button
        fun refreshGenerateState() {
            val ready = startDate != null && endDate != null
            generateButton.isEnabled = ready
            generateButton.alpha = if (ready) 1f else 0.6f
            dateHint.visibility = if (ready) View.GONE else View.VISIBLE
            if (ready) dateError.visibility = View.GONE
        }

        val startDateInput = buildDateInput("Start Date") { picked ->
            startDate = picked
            refreshGenerateState()
        }
        val endDateInput = buildDateInput("End Date") { picked ->
            endDate = picked
            refreshGenerateState()
        }

        root.addView(startDateInput.wrapper)
        root.addView(endDateInput.wrapper)
        root.addView(dateError)

        var attendeeQuery = ""
        if (OrganizerReportsRepository.attendeeQueryApplicable.contains(item.reportType)) {
            root.addView(labeledSearchInput("Attendee Search (Name or ID)") { attendeeQuery = it })
        }

        if (OrganizerReportsRepository.transactionStatusApplicable.contains(item.reportType)) {
            root.addView(statusSelector { status = it })
        }

        generateButton = primaryButton("Generate") {
            if (endDate!!.isBefore(startDate!!)) {
                dateError.text = "End date must be after start date"
                dateError.visibility = View.VISIBLE
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

        val skipButton = ghostButton("Skip filters / View All") {
            generateSingleReport(item, OrganizerReportsRepository.defaultFilters(), dialog, root)
        }

        root.addView(dateHint.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, 0)
            }
        })
        root.addView(generateButton.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                setMargins(0, 0, 0, dp(8))
            }
        })
        root.addView(skipButton.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
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
        MainScope().launch {
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
                        .setAction("Retry") { generateSingleReport(item, filters, dialog, sheetRoot) }
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
            .setTitle("Generating reports")
            .setMessage("Please wait while all report sections are prepared.")
            .setCancelable(false)
            .create()
        loading.show()

        MainScope().launch {
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
    }

    private fun reportCatalog(): List<EventReportCatalogItem> = listOf(
        EventReportCatalogItem(EventReportType.ROSTER, "Attendee Roster Report", R.drawable.ic_group, Color.parseColor("#6366F1"), Color.parseColor("#EEF2FF")),
        EventReportCatalogItem(EventReportType.NO_SHOWS, "Not Checked In Report", R.drawable.ic_nav_profile, Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2")),
        EventReportCatalogItem(EventReportType.ENTRY_LOGS, "Entry Logs Report", R.drawable.ic_scan, Color.parseColor("#8B5CF6"), Color.parseColor("#F3E8FF")),
        EventReportCatalogItem(EventReportType.ATTENDANCE, "Attendance Report", R.drawable.ic_organizer_bar_chart, Color.parseColor("#4F46E5"), Color.parseColor("#EEF2FF")),
        EventReportCatalogItem(EventReportType.CLAIMS, "Benefit Claims Report", R.drawable.ic_nav_gift, Color.parseColor("#F59E0B"), Color.parseColor("#FEF3C7")),
        EventReportCatalogItem(EventReportType.BOOTH_VISITS, "Booth/Session Visits Report", R.drawable.ic_calendar, Color.parseColor("#10B981"), Color.parseColor("#DCFCE7")),
        EventReportCatalogItem(EventReportType.EXIT_LOGS, "Exit Logs Report", R.drawable.ic_chevron_right, Color.parseColor("#0EA5E9"), Color.parseColor("#E0F2FE")),
        EventReportCatalogItem(EventReportType.POINTS, "Points Report", R.drawable.ic_organizer_reports, Color.parseColor("#06B6D4"), Color.parseColor("#CFFAFE")),
    )

    private data class DateInputHolder(val wrapper: LinearLayout, val valueView: TextView)

    private fun buildDateInput(label: String, onChanged: (LocalDate?) -> Unit): DateInputHolder {
        val valueView = text("Select", 14, false, MUTED)
        val wrapper = card(12).apply {
            addView(text(label, 13, true))
            addView(row().apply {
                setPadding(0, dp(8), 0, 0)
                addView(valueView.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(ImageView(this@EventReportsActivity).apply {
                    setImageResource(R.drawable.ic_calendar)
                    setColorFilter(MUTED)
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                })
            })
            setOnClickListener {
                val today = LocalDate.now(ZoneId.of("Asia/Manila"))
                android.app.DatePickerDialog(
                    this@EventReportsActivity,
                    { _, year, month, dayOfMonth ->
                        val picked = LocalDate.of(year, month + 1, dayOfMonth)
                        valueView.text = dateFormatter.format(picked)
                        valueView.setTextColor(TEXT)
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

    private fun labeledSearchInput(label: String, onChanged: (String) -> Unit): LinearLayout = card(12).apply {
        addView(text(label, 13, true))
        addView(EditText(this@EventReportsActivity).apply {
            hint = "Type a name or attendee ID"
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.parseColor("#F9FAFB"), 10, BORDER, density = resources.displayMetrics.density)
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            afterTextChanged { onChanged(text.toString()) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(8), 0, 0)
            }
        })
    }

    private fun statusSelector(onSelected: (EventReportFilterStatus) -> Unit): LinearLayout = card(12).apply {
        addView(text("Transaction Status", 13, true))
        val chipRow = row().apply {
            setPadding(0, dp(10), 0, 0)
            gravity = Gravity.START
        }

        fun statusChip(label: String, status: EventReportFilterStatus): TextView {
            return chip(label, status == EventReportFilterStatus.ALL).apply {
                setOnClickListener {
                    onSelected(status)
                    for (idx in 0 until chipRow.childCount) {
                        val child = chipRow.getChildAt(idx) as? TextView ?: continue
                        val isActive = child.text.toString().equals(label, ignoreCase = true)
                        child.setTextColor(if (isActive) Color.WHITE else PRIMARY)
                        child.background = rounded(
                            if (isActive) PRIMARY else Color.WHITE,
                            18,
                            if (isActive) null else BORDER,
                            density = resources.displayMetrics.density,
                        )
                    }
                }
            }
        }

        chipRow.addView(statusChip("All", EventReportFilterStatus.ALL))
        chipRow.addView(statusChip("Approved", EventReportFilterStatus.APPROVED))
        chipRow.addView(statusChip("Rejected", EventReportFilterStatus.REJECTED))
        addView(chipRow)
    }
}
