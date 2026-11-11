package com.thedavelopers.eventqr.features.organizer.reports

import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.mediumWeight
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenFloat
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.roundedRect
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import android.graphics.drawable.ColorDrawable
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
import androidx.core.content.ContextCompat
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
import com.thedavelopers.eventqr.ui.components.ListRowBinder
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
                iconRes = AppIcons.Report,
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
        // 56dp field row, not a glassy card wrapping a hidden spinner.
        container.addView(
            eventSelector(loadedEvents, selectedEvent.id) { event ->
                selectedEvent = event
                repository.saveSelectedEventId(event.id)
                saveSelectedEventId(event.id)
                loadScreen()
            },
        )
        container.addView(buildSummaryMetrics())
        container.addView(sectionHeader(getString(R.string.event_reports_generate_reports)))
        container.addView(buildReportCatalogContainer(reportCatalog()))
    }

    /**
     * REGISTERED / CHECKED IN / EXITED as three 56dp metric rows (§5.9 form 2). This replaces the
     * dark gradient summary card with its three translucent tiles.
     */
    private fun buildSummaryMetrics(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_8)) }
        addView(metricRow(getString(R.string.ui_organizer_report_registered), summary.registeredCount))
        addView(metricRow(getString(R.string.ui_organizer_report_checked_in), summary.checkedInCount))
        addView(metricRow(getString(R.string.ui_organizer_report_exited), summary.exitedCount))
    }

    /** label left, value right, hairline under - no card, no fill, no icon (§5.9). */
    private fun metricRow(label: String, value: Int): View {
        val row = layoutInflater.inflate(R.layout.widget_metric_row, null, false)
        row.findViewById<TextView>(R.id.metricLabel)?.text = label
        row.findViewById<TextView>(R.id.metricValue)?.text = formatCount(value)
        return row
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
                setImageResource(AppIcons.Chevron)
                setColorFilter(color(R.color.primary))
                layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.ui_chevron), dimenPx(R.dimen.ui_chevron)).apply {
                    marginStart = dimenPx(R.dimen.space_xxs)
                }
            })
        }
        addView(actionRow)
    }

    /**
     * The five report types as one grouped card of hairline rows (§5.3 CardList, §5.5). Each row
     * carries ONE icon weight (20dp `ui_primary` in the neutral tile) and one chevron - the
     * per-row tinted icon rainbow is deleted.
     */
    private fun buildReportCatalogContainer(items: List<EventReportCatalogItem>): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_ui_card)
            elevation = 0f
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dimenPx(R.dimen.space_24)) }
        }

        items.forEachIndexed { index, item ->
            container.addView(buildReportMenuItem(item, isLast = index == items.lastIndex) { openFilterSheet(item) })
        }

        return container
    }

    private fun buildReportMenuItem(
        item: EventReportCatalogItem,
        isLast: Boolean,
        onClick: () -> Unit,
    ): View {
        val row = layoutInflater.inflate(R.layout.list_row_icon, null, false)
        ListRowBinder.bind(
            row = row,
            title = item.label,
            meta = getReportSubtitle(item.reportType),
            trailing = ListRowBinder.Trailing.CHEVRON,
            showDivider = !isLast,
            onClick = onClick,
        )
        row.findViewById<ImageView>(R.id.listRowIcon)?.setImageResource(item.iconRes)
        return row
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

    /** One parameterized skeleton (§5.11): the same geometry as the loaded selector + metric rows. */
    private fun reportsSkeleton(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutInflater.inflate(R.layout.include_skeleton_card, this, true)
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
        EventReportCatalogItem(EventReportType.ROSTER, getString(R.string.event_reports_title_roster), AppIcons.People, color(R.color.chart_5), color(R.color.accent_container)),
        EventReportCatalogItem(EventReportType.NO_SHOWS, getString(R.string.event_reports_title_no_shows), AppIcons.Profile, color(R.color.danger), color(R.color.error_container)),
        EventReportCatalogItem(EventReportType.ENTRY_LOGS, getString(R.string.event_reports_title_entry_logs), AppIcons.Scan, color(R.color.catalog_tint_violet), color(R.color.tile_violet)),
        EventReportCatalogItem(EventReportType.ATTENDANCE, getString(R.string.event_reports_title_attendance), AppIcons.Report, color(R.color.chart_1), color(R.color.accent_container)),
        EventReportCatalogItem(EventReportType.CLAIMS, getString(R.string.event_reports_title_claims), AppIcons.Reward, color(R.color.chart_7), color(R.color.warning_container)),
        EventReportCatalogItem(EventReportType.BOOTH_VISITS, getString(R.string.event_reports_title_booth_visits), AppIcons.Event, color(R.color.chart_6), color(R.color.success_container)),
        EventReportCatalogItem(EventReportType.EXIT_LOGS, getString(R.string.event_reports_title_exit_logs), AppIcons.Chevron, color(R.color.chart_4), color(R.color.info_container)),
        EventReportCatalogItem(EventReportType.POINTS, getString(R.string.event_reports_title_points), AppIcons.Report, color(R.color.catalog_tint_cyan), color(R.color.tile_cyan)),
    )

    private data class DateInputHolder(val wrapper: LinearLayout, val valueView: TextView)

    private fun buildDateInput(label: String, onChanged: (LocalDate?) -> Unit): DateInputHolder {
        val valueView = styledText(getString(R.string.event_reports_select), R.dimen.ui_type_body, false, orgColors.muted)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@EventReportsActivity).apply {
                    text = label
                    setTextAppearance(R.style.TextAppearance_EventQR_Type_FieldLabel)
                    setPadding(0, dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_8))
                },
            )
            addView(
                row().apply {
                    minimumHeight = dimenPx(R.dimen.ui_control_height)
                    background = ContextCompat.getDrawable(context, R.drawable.bg_ui_field)
                    setPadding(dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_12), 0)
                    addView(valueView.apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(ImageView(this@EventReportsActivity).apply {
                        setImageResource(AppIcons.Event)
                        imageTintList = android.content.res.ColorStateList.valueOf(orgColors.muted)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        layoutParams = LinearLayout.LayoutParams(dimenPx(R.dimen.icon_md), dimenPx(R.dimen.icon_md))
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
                },
            )
        }
        return DateInputHolder(wrapper, valueView)
    }

    private fun labeledSearchInput(label: String, onChanged: (String) -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(this@EventReportsActivity).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_EventQR_Type_FieldLabel)
                setPadding(0, dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_8))
            },
        )
        addView(EditText(this@EventReportsActivity).apply {
            hint = getString(R.string.event_reports_attendee_search_hint)
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Body)
            setPadding(dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_16), 0)
            background = ContextCompat.getDrawable(context, R.drawable.bg_ui_field)
            minimumHeight = dimenPx(R.dimen.ui_control_height)
            setTextColor(orgColors.text)
            setHintTextColor(orgColors.muted)
            afterTextChanged { onChanged(text.toString()) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.ui_control_height))
        })
    }

    private fun statusSelector(onSelected: (EventReportFilterStatus) -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(this@EventReportsActivity).apply {
                text = getString(R.string.event_reports_transaction_status)
                setTextAppearance(R.style.TextAppearance_EventQR_Type_FieldLabel)
                setPadding(0, dimenPx(R.dimen.space_16), 0, dimenPx(R.dimen.space_8))
            },
        )
        val chipRow = row().apply {
            setPadding(0, dimenPx(R.dimen.space_8), 0, 0)
            gravity = Gravity.START
        }

        fun statusChip(label: String, status: EventReportFilterStatus): TextView {
            return chip(label, status == EventReportFilterStatus.ALL).apply {
                setOnClickListener {
                    onSelected(status)
                    for (idx in 0 until chipRow.childCount) {
                        val child = chipRow.getChildAt(idx) as? TextView ?: continue
                        val isActive = child.text.toString().equals(label, ignoreCase = true)
                        child.isSelected = isActive
                        child.setTextColor(
                            ContextCompat.getColor(
                                context,
                                if (isActive) R.color.ui_on_primary else R.color.ui_text_primary,
                            ),
                        )
                        child.setBackgroundResource(
                            if (isActive) R.drawable.bg_ui_chip_selected else R.drawable.bg_ui_chip_unselected,
                        )
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
