package com.thedavelopers.eventqr.features.organizer.reports

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

open class EventReportsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var reportsRepository: OrganizerReportsRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var content: LinearLayout
    private var summary: EventReportSummaryDto = EventReportSummaryDto()
    private var loadJob: Job? = null
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        reportsRepository = OrganizerReportsRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Event Reports")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Event Reports")
        content = organizerShell(
            title = "Event Reports",
            selectedNav = NAV_REPORTS,
        )
        val report = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(report)
        loadScreen()
    }

    private fun loadScreen() {
        loadJob?.cancel()
        content.removeAllViews()

        val summaryContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(summaryContainer)
        summaryContainer.addView(loadingState("Loading report summary..."))

        loadJob = lifecycleScope.launch {
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
        container.removeAllViews()
        container.addView(card().apply {
            addView(text("Select Event", 13, false, MUTED))
            addView(eventSelector(repository.getApprovedOrganizerEvents(), selectedEvent.id) {
                selectedEvent = it
                repository.saveSelectedEventId(it.id)
                saveSelectedEventId(it.id)
                loadScreen()
            })
        })
        container.addView(buildSummaryHeaderCard())
        container.addView(sectionHeader("Generate Reports"))
        reportCatalog().forEach { item ->
            container.addView(menuCard(
                label = item.label,
                iconRes = item.iconRes,
                iconTint = item.iconTint,
                iconBg = item.iconBg,
            ) {
                openFilterSheet(item)
            })
        }
    }

    private fun buildSummaryHeaderCard(): LinearLayout {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#25215F"), Color.parseColor("#4F46E5")),
        ).apply {
            cornerRadius = dp(16).toFloat()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(8), 0, dp(14)) }

            addView(text("${selectedEvent.title} • Overview", 21, true, Color.WHITE))
            addView(row().apply {
                setPadding(0, dp(14), 0, 0)
                addView(summaryStat("Registered", summary.registeredCount))
                addView(summaryStat("Checked In", summary.checkedInCount))
                addView(summaryStat("Exited", summary.exitedCount))
            })
        }
    }

    private fun summaryStat(label: String, value: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(text(formatCount(value), 30, true, Color.parseColor("#22D3EE")))
        addView(text(label, 13, false, Color.parseColor("#CDD6F5")))
    }

    private fun sectionHeader(label: String): LinearLayout = row().apply {
        setPadding(dp(2), dp(6), dp(2), dp(6))
        addView(text(label, 22, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(text("Generate All", 15, true, Color.parseColor("#4F46E5")).apply {
            setOnClickListener { generateAllReports() }
        })
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
                dateError.text = "End date must be on or after start date."
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
        EventReportCatalogItem(EventReportType.NO_SHOWS, "No-Shows Report", R.drawable.ic_nav_profile, Color.parseColor("#EF4444"), Color.parseColor("#FEE2E2")),
        EventReportCatalogItem(EventReportType.ENTRY_LOGS, "Entry Logs Report", R.drawable.ic_qr_scan, Color.parseColor("#8B5CF6"), Color.parseColor("#F3E8FF")),
        EventReportCatalogItem(EventReportType.ATTENDANCE, "Attendance Report", R.drawable.ic_organizer_bar_chart, Color.parseColor("#4F46E5"), Color.parseColor("#EEF2FF")),
        EventReportCatalogItem(EventReportType.CLAIMS, "Benefit Claims Report", R.drawable.ic_gift, Color.parseColor("#F59E0B"), Color.parseColor("#FEF3C7")),
        EventReportCatalogItem(EventReportType.BOOTH_VISITS, "Booth/Session Visits Report", R.drawable.ic_nav_calendar, Color.parseColor("#10B981"), Color.parseColor("#DCFCE7")),
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
                    setImageResource(R.drawable.ic_nav_calendar)
                    setColorFilter(MUTED)
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                })
            })
            setOnClickListener {
                val today = LocalDate.now()
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
