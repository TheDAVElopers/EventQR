package com.thedavelopers.eventqr.features.organizer.reports

import androidx.lifecycle.lifecycleScope
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportEmptyState
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportRowDto
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class ReportPreviewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_REPORT = "report"
        private const val EXTRA_REPORTS = "reports"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_SOURCE_FILTERS = "source_filters"
        private const val EXTRA_IS_COMBINED = "is_combined"

        // Extras travel as JSON, not java.io.Serializable: Java serialization walks fields by
        // reflection and throws NotSerializableException under R8 (release builds only).
        private val extrasGson = GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ -> JsonPrimitive(src.toString()) })
            .registerTypeAdapter(LocalDate::class.java, JsonDeserializer<LocalDate> { json, _, _ -> LocalDate.parse(json.asString) })
            .create()

        private fun <T> Intent.getJsonExtra(name: String, type: Class<T>): T? {
            val json = getStringExtra(name) ?: return null
            return try {
                extrasGson.fromJson(json, type)
            } catch (e: Exception) {
                android.util.Log.e("ReportPreview", "Failed to read extra $name", e)
                null
            }
        }

        fun newSingleIntent(
            context: Context,
            eventId: String,
            report: EventReportDto,
            summary: EventReportSummaryDto,
            sourceFilters: EventReportFiltersDto,
        ): Intent {
            return Intent(context, ReportPreviewActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_REPORT, extrasGson.toJson(report))
                putExtra(EXTRA_SUMMARY, extrasGson.toJson(summary))
                putExtra(EXTRA_SOURCE_FILTERS, extrasGson.toJson(sourceFilters))
                putExtra(EXTRA_IS_COMBINED, false)
            }
        }

        fun newCombinedIntent(
            context: Context,
            eventId: String,
            reports: List<EventReportDto>,
            summary: EventReportSummaryDto,
        ): Intent {
            return Intent(context, ReportPreviewActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_REPORTS, extrasGson.toJson(reports))
                putExtra(EXTRA_SUMMARY, extrasGson.toJson(summary))
                putExtra(EXTRA_IS_COMBINED, true)
            }
        }
    }

    private lateinit var content: LinearLayout
    private var eventId: String = ""
    private var isCombined = false
    private var singleReport: EventReportDto? = null
    private var combinedReports: List<EventReportDto>? = null
    private var summary: EventReportSummaryDto = EventReportSummaryDto()
    private var sourceFilters: EventReportFiltersDto = EventReportFiltersDto()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Manila"))


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return finishWithError(getString(R.string.report_preview_event_id_missing))
        isCombined = intent.getBooleanExtra(EXTRA_IS_COMBINED, false)
        summary = intent.getJsonExtra(EXTRA_SUMMARY, EventReportSummaryDto::class.java) ?: EventReportSummaryDto()

        if (isCombined) {
            combinedReports = intent.getJsonExtra(EXTRA_REPORTS, Array<EventReportDto>::class.java)?.toList() ?: emptyList()
        } else {
            singleReport = intent.getJsonExtra(EXTRA_REPORT, EventReportDto::class.java)
            sourceFilters = intent.getJsonExtra(EXTRA_SOURCE_FILTERS, EventReportFiltersDto::class.java) ?: EventReportFiltersDto()
        }

        if (!isCombined && singleReport == null) {
            return finishWithError(getString(R.string.report_preview_report_data_missing))
        }

        val reportTitle = if (isCombined) getString(R.string.report_preview_combined_report) else (singleReport?.reportTitle ?: getString(R.string.report_preview_report))
        content = organizerShell(
            title = getString(R.string.report_preview_title),
            selectedNav = NAV_REPORTS,
            showBack = true,
            topRightLabel = getString(R.string.report_preview_export),
            onTopRight = { showExportDialog() },
        )

        render()
    }

    private fun render() {
        content.removeAllViews()

        // Report header info
        content.addView(card(16).apply {
            val report = singleReport ?: combinedReports?.firstOrNull()

            // Header title row with title and badge
            val titleRow = LinearLayout(this@ReportPreviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val pageTitle = if (isCombined) getString(R.string.report_preview_combined_title) else (report?.reportTitle ?: getString(R.string.report_preview_report))
            titleRow.addView(text(pageTitle, 18, true, TEXT).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            val recordBadge = TextView(this@ReportPreviewActivity).apply {
                text = if (isCombined) getString(R.string.report_preview_reports_count, combinedReports?.size ?: 0) else getString(R.string.report_preview_records_count_caps, report?.rows?.size ?: 0)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(NAV_PURPLE)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = rounded(Color.parseColor("#EEF2FF"), 6, null, density = resources.displayMetrics.density)
            }
            titleRow.addView(recordBadge)
            addView(titleRow)

            // Event Name
            summary.eventName?.takeIf { it.isNotBlank() }?.let { eventName ->
                addView(text(eventName, 13, true, Color.parseColor("#374151")).apply {
                    setPadding(0, dp(4), 0, 0)
                })
            }

            // Generated date & time
            // Server generatedAt only; never invent a client-side time.
            report?.generatedAtInstant?.let { generatedAt ->
                addView(text(getString(R.string.report_preview_generated_at, dateFormatter.format(generatedAt)), 12, false, MUTED).apply {
                    setPadding(0, dp(2), 0, dp(4))
                })
            }

            // Filters
            if (!isCombined) {
                addView(buildFilterChips(sourceFilters).apply { setPadding(0, dp(6), 0, 0) })
            } else {
                addView(text(getString(R.string.report_preview_combined_contains), 12, false, MUTED).apply {
                    setPadding(0, dp(4), 0, 0)
                })
            }
        })

        if (isCombined) {
            combinedReports?.forEach { report ->
                renderReportSection(report)
            }
        } else {
            singleReport?.let { renderReportSection(it) }
        }
    }

    private fun buildFilterChips(filters: EventReportFiltersDto): LinearLayout = row().apply {
        gravity = Gravity.START
        if (filters.startDate != null || filters.endDate != null) {
            val start = filters.startDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: getString(R.string.report_preview_filter_start)
            val end = filters.endDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: getString(R.string.report_preview_filter_end)
            addView(chip(getString(R.string.report_preview_chip_date, start, end), false, PRIMARY))
        }
        if (filters.attendeeQuery?.isNotBlank() == true) {
            addView(chip(getString(R.string.report_preview_chip_attendee, filters.attendeeQuery.orEmpty()), false, PRIMARY))
        }
        if (filters.status != EventReportFilterStatus.ALL) {
            addView(chip(getString(R.string.report_preview_chip_status, filters.status.name), false, PRIMARY))
        }
        if (filters.startDate == null && filters.endDate == null && filters.attendeeQuery.isNullOrBlank() && filters.status == EventReportFilterStatus.ALL) {
            addView(chip(getString(R.string.report_preview_chip_no_filters), false, MUTED))
        }
    }

    private fun renderReportSection(report: EventReportDto) {
        if (isCombined) {
            val sectionHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(16), dp(4), dp(4))
                addView(text(report.reportTitle ?: getString(R.string.report_preview_section), 16, true, PRIMARY).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(text(getString(R.string.report_preview_records_count, report.rows.size), 12, false, MUTED))
            }
            content.addView(sectionHeader)
        }

        // Chart tailored to information type
        if (report.chartSeries.isNotEmpty()) {
            if (isDistributionReport(report)) {
                content.addView(DonutDistributionChartView(this, report))
            } else {
                content.addView(HorizontalRankedBarChartView(this, report))
            }
        }

        // Empty states
        when (report.emptyState) {
            EventReportEmptyState.NO_FILTER_MATCH -> {
                content.addView(emptyState(
                    iconRes = R.drawable.ic_organizer_reports,
                    title = getString(R.string.report_preview_no_matching_title),
                    subtext = getString(R.string.report_preview_no_matching_subtext),
                    actionLabel = getString(R.string.report_preview_back_to_filters),
                    onAction = { finish() },
                ))
            }
            EventReportEmptyState.NO_EVENT_RECORDS -> {
                content.addView(emptyState(
                    iconRes = R.drawable.ic_organizer_reports,
                    title = getString(R.string.report_preview_no_records_title),
                    subtext = getString(R.string.report_preview_no_records_subtext),
                ))
            }
            else -> {
                // Data table
                if (report.rows.isNotEmpty()) {
                    content.addView(buildPaginatedDataTable(report))
                } else {
                    content.addView(emptyState(
                        iconRes = R.drawable.ic_organizer_reports,
                        title = getString(R.string.report_preview_no_data_title),
                        subtext = getString(R.string.report_preview_no_data_subtext),
                    ))
                }
            }
        }
    }

    private fun isDistributionReport(report: EventReportDto): Boolean {
        return when (report.reportType) {
            EventReportType.ROSTER,
            EventReportType.NO_SHOWS,
            EventReportType.ENTRY_LOGS,
            EventReportType.CLAIMS,
            EventReportType.EXIT_LOGS -> true
            EventReportType.ATTENDANCE,
            EventReportType.BOOTH_VISITS,
            EventReportType.POINTS -> false
        }
    }

    private fun getChartTitle(report: EventReportDto): String {
        return when (report.reportType) {
            EventReportType.ROSTER -> getString(R.string.report_preview_chart_title_roster)
            EventReportType.NO_SHOWS -> getString(R.string.report_preview_chart_title_no_shows)
            EventReportType.ENTRY_LOGS -> getString(R.string.report_preview_chart_title_entry_logs)
            EventReportType.ATTENDANCE -> getString(R.string.report_preview_chart_title_attendance)
            EventReportType.CLAIMS -> getString(R.string.report_preview_chart_title_claims)
            EventReportType.BOOTH_VISITS -> getString(R.string.report_preview_chart_title_booth_visits)
            EventReportType.EXIT_LOGS -> getString(R.string.report_preview_chart_title_exit_logs)
            EventReportType.POINTS -> getString(R.string.report_preview_chart_title_points)
        }
    }

    private fun getChartSubtitle(report: EventReportDto): String {
        return when (report.reportType) {
            EventReportType.ROSTER -> getString(R.string.report_preview_roster_chart_subtitle)
            EventReportType.NO_SHOWS -> getString(R.string.report_preview_chart_subtitle_no_shows)
            EventReportType.ENTRY_LOGS -> getString(R.string.report_preview_chart_subtitle_entry_logs)
            EventReportType.ATTENDANCE -> getString(R.string.report_preview_attendance_chart_subtitle)
            EventReportType.CLAIMS -> getString(R.string.report_preview_claims_chart_subtitle)
            EventReportType.BOOTH_VISITS -> getString(R.string.report_preview_booth_chart_subtitle)
            EventReportType.EXIT_LOGS -> getString(R.string.report_preview_chart_subtitle_exit_logs)
            EventReportType.POINTS -> getString(R.string.report_preview_chart_subtitle_points)
        }
    }

    private fun getSliceColor(label: String, index: Int): Int {
        val lower = label.lowercase()
        return when {
            label == OTHER_CHART_LABEL -> Color.parseColor("#9CA3AF")
            lower.contains("success") || lower.contains("entered") || lower.contains("completed") -> Color.parseColor("#10B981")
            lower.contains("no show") || lower.contains("invalid") || lower.contains("cancelled") || lower.contains("failed") -> Color.parseColor("#EF4444")
            lower.contains("duplicate") || lower.contains("not entered") || lower.contains("already") || lower.contains("warning") -> Color.parseColor("#F59E0B")
            lower.contains("registered") -> Color.parseColor("#4F46E5")
            else -> {
                val palette = listOf(
                    Color.parseColor("#4F46E5"),
                    Color.parseColor("#06B6D4"),
                    Color.parseColor("#8B5CF6"),
                    Color.parseColor("#0EA5E9"),
                    Color.parseColor("#6366F1"),
                    Color.parseColor("#10B981"),
                    Color.parseColor("#F59E0B")
                )
                palette[index % palette.size]
            }
        }
    }

    private fun getColumnWeight(index: Int, columnCount: Int, reportType: EventReportType): Float {
        return when (reportType) {
            EventReportType.CLAIMS -> {
                when (index) {
                    0 -> 1.0f // Name
                    1 -> 1.1f // Benefit
                    2 -> 1.1f // Claimed At
                    else -> 0.8f // Result
                }
            }
            EventReportType.ATTENDANCE, EventReportType.BOOTH_VISITS -> {
                when (index) {
                    0 -> 1.0f // Name
                    1 -> 1.4f // Session/Activity or Booth/Session
                    else -> 1.0f // Timestamp / Visit Time
                }
            }
            EventReportType.POINTS -> {
                when (index) {
                    0 -> 1.0f // Name
                    1 -> 0.8f // Points (+/-)
                    else -> 1.4f // Source Activity
                }
            }
            EventReportType.ENTRY_LOGS, EventReportType.EXIT_LOGS -> {
                when (index) {
                    0 -> 1.1f // Name
                    1 -> 1.2f // Entry/Exit Time
                    else -> 0.9f // Result
                }
            }
            EventReportType.ROSTER -> {
                when (index) {
                    0 -> 1.1f // Name
                    1 -> 0.9f // Status
                    else -> 1.1f // Registered At
                }
            }
            EventReportType.NO_SHOWS -> {
                when (index) {
                    0 -> 1.1f // Name
                    1 -> 1.0f // Registered On
                    else -> 1.0f // Reason
                }
            }
        }
    }

    private fun getColumnGravity(index: Int, columnCount: Int, reportType: EventReportType): Int {
        return Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun buildPaginatedDataTable(report: EventReportDto): LinearLayout {
        return card(16).apply {
            // Header Row: Section Title + Record count pill
            val headerRow = LinearLayout(this@ReportPreviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(12))
                }
            }

            headerRow.addView(text(getString(R.string.report_preview_detailed_records), 15, true, TEXT).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            val countBadge = TextView(this@ReportPreviewActivity).apply {
                text = getString(R.string.report_preview_records_count, report.rows.size)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(MUTED)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = rounded(Color.parseColor("#F3F4F6"), 6, null, density = resources.displayMetrics.density)
            }
            headerRow.addView(countBadge)
            addView(headerRow)

            val columnCount = report.columns.size

            // Enclosed Table Container with rounded border
            val tableContainer = LinearLayout(this@ReportPreviewActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Color.WHITE, 8, BORDER, density = resources.displayMetrics.density)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Table Header row
            val colHeader = LinearLayout(this@ReportPreviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#F9FAFB"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val isFourCol = columnCount >= 4
            report.columns.forEachIndexed { index, column ->
                colHeader.addView(TextView(this@ReportPreviewActivity).apply {
                    text = displayColumnName(report.reportType, index, column)
                    textSize = if (isFourCol) 11f else 12f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#4B5563"))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    gravity = getColumnGravity(index, columnCount, report.reportType)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setPadding(0, 0, dp(6), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        getColumnWeight(index, columnCount, report.reportType)
                    )
                })
            }
            tableContainer.addView(colHeader)

            // Divider under column headers
            val headerDivider = View(this@ReportPreviewActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(BORDER)
            }
            tableContainer.addView(headerDivider)

            // RecyclerView for rows
            val recyclerView = RecyclerView(this@ReportPreviewActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutManager = LinearLayoutManager(this@ReportPreviewActivity)
                adapter = ReportRowAdapter(report)
                isNestedScrollingEnabled = false
                setHasFixedSize(true)
            }
            tableContainer.addView(recyclerView)

            addView(tableContainer)
        }
    }

    private inner class ReportRowAdapter(
        private val report: EventReportDto
    ) : RecyclerView.Adapter<ReportRowAdapter.ViewHolder>() {

        private val rows = report.rows
        private val columnCount = report.columns.size
        private val isFourCol = columnCount >= 4

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val rowContainer: LinearLayout = itemView as LinearLayout
            val views: MutableList<TextView> = mutableListOf()

            fun setupViews() {
                rowContainer.removeAllViews()
                views.clear()
                for (i in 0 until columnCount) {
                    val tv = TextView(rowContainer.context).apply {
                        textSize = if (isFourCol) 11f else 12f
                        setIncludeFontPadding(false)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = getColumnGravity(i, columnCount, report.reportType)
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        setPadding(0, 0, dp(6), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            getColumnWeight(i, columnCount, report.reportType)
                        )
                    }
                    views.add(tv)
                    rowContainer.addView(tv)
                }
            }

            fun bind(rowIndex: Int) {
                val row = rows[rowIndex]
                if (rowIndex % 2 == 0) {
                    rowContainer.setBackgroundColor(Color.WHITE)
                } else {
                    rowContainer.setBackgroundColor(Color.parseColor("#F9FAFB"))
                }

                for (i in views.indices) {
                    val value = if (i < row.values.size) row.values[i] else null
                    val isPointsCell = report.reportType == EventReportType.POINTS && i == 1
                    val pointsCell = if (isPointsCell) formatPointsCell(value) else null
                    val displayValue = pointsCell?.text ?: (value?.ifBlank { "—" } ?: "—")
                    views[i].text = displayValue
                    val isFirst = (i == 0)
                    views[i].gravity = getColumnGravity(i, columnCount, report.reportType)
                    views[i].textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    views[i].setTypeface(null, if (isFirst) Typeface.BOLD else Typeface.NORMAL)

                    val lower = displayValue.lowercase()
                    val cellColor = when {
                        pointsCell?.sign == PointsSign.NEGATIVE -> Color.parseColor("#DC2626")
                        pointsCell?.sign == PointsSign.POSITIVE -> Color.parseColor("#059669")
                        lower == "success" || lower == "entered" || lower == "completed" -> Color.parseColor("#059669")
                        lower == "no show" || lower == "marked no show" || lower == "invalid" || lower == "failed" -> Color.parseColor("#DC2626")
                        lower == "duplicate" || lower == "already claimed" || lower == "not entered" -> Color.parseColor("#D97706")
                        isFirst -> Color.parseColor("#111827")
                        else -> Color.parseColor("#4B5563")
                    }
                    views[i].setTextColor(cellColor)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val holder = ViewHolder(row)
            holder.setupViews()
            return holder
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = rows.size
    }

    private fun showExportDialog() {
        val rootView = content.rootView ?: content
        if (isCombined && combinedReports.isNullOrEmpty()) {
            Snackbar.make(rootView, getString(R.string.report_preview_no_data_available_export), Snackbar.LENGTH_LONG).show()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(if (isCombined) R.string.report_preview_export_combined_report else R.string.report_preview_export_report))
            .setMessage(getString(R.string.report_preview_choose_export_format))
            .setPositiveButton(getString(R.string.report_preview_csv)) { _, _ -> exportReport("CSV") }
            .setNegativeButton(getString(R.string.report_preview_pdf)) { _, _ -> exportReport("PDF") }
            .setNeutralButton(getString(R.string.request_event_cancel), null)
            .create()
        dialog.show()
    }

    private var exportJob: Job? = null

    private fun exportReport(format: String) {
        if (exportJob?.isActive == true) return // ignore double taps while an export runs
        val rootView = content.rootView ?: content
        val reports = if (isCombined) combinedReports.orEmpty() else listOfNotNull(singleReport)
        if (reports.isEmpty()) {
            Snackbar.make(rootView, getString(R.string.report_preview_no_data_export), Snackbar.LENGTH_LONG).show()
            return
        }

        val exportFormat = if (format.equals("PDF", ignoreCase = true)) ReportExportFormat.PDF else ReportExportFormat.CSV
        val preparing = if (isCombined) R.string.report_preview_preparing_combined else R.string.report_preview_preparing
        Snackbar.make(rootView, getString(preparing, format), Snackbar.LENGTH_SHORT).show()

        exportJob = lifecycleScope.launch {
            try {
                val fileName = ReportFileNames.build(summary.eventName, reports, exportFormat)
                val contentType = getString(if (exportFormat == ReportExportFormat.PDF) R.string.report_preview_application_pdf else R.string.report_preview_text_csv)
                val exporter = reportExporter(reports)
                val bytes = withContext(Dispatchers.Default) {
                    if (exportFormat == ReportExportFormat.PDF) exporter.pdf(reports) else exporter.csv(reports)
                }
                saveAndShareFile(bytes, fileName, contentType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Snackbar.make(rootView, getString(R.string.report_preview_export_failed, e.message.orEmpty()), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.common_retry)) { exportReport(format) }
                    .show()
            }
        }
    }

    private fun reportExporter(reports: List<EventReportDto>): ReportExporter {
        val labels = ReportExportLabels(
            event = getString(R.string.report_preview_event),
            generated = getString(R.string.report_preview_generated_label),
            summary = getString(R.string.report_export_summary),
            category = getString(R.string.report_export_category),
            count = getString(R.string.report_export_count),
            total = getString(R.string.report_export_total),
            noRecords = getString(R.string.report_preview_pdf_no_records),
            combinedTitle = getString(R.string.report_preview_combined_title),
            sectionFormat = getString(R.string.report_preview_section_n),
            recordsFormat = getString(R.string.report_export_records),
            pageFormat = getString(R.string.report_export_page),
        )
        val eventName = summary.eventName?.takeIf { it.isNotBlank() } ?: labels.event
        return ReportExporter(labels, eventName, dateFormatter.format(combinedGeneratedAt(reports)))
    }

    private fun emptyChartTextRes(type: EventReportType): Int = when (type) {
        EventReportType.POINTS -> R.string.report_preview_empty_points
        EventReportType.ATTENDANCE -> R.string.report_preview_empty_attendance
        EventReportType.BOOTH_VISITS -> R.string.report_preview_empty_booth
        else -> R.string.report_preview_empty_chart
    }

    private fun combinedGeneratedAt(reports: List<EventReportDto>): Instant =
        reports.mapNotNull { it.generatedAtInstant }.maxOrNull() ?: Instant.now()

    private fun saveAndShareFile(bytes: ByteArray, fileName: String, contentType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToPublicDownloads(bytes, fileName, contentType)
        } else {
            saveToLegacyPrivateStorage(bytes, fileName, contentType)
        }
    }

    private fun showSavedMessage(message: String) {
        Snackbar.make(content, message, Snackbar.LENGTH_LONG).show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToPublicDownloads(bytes: ByteArray, fileName: String, contentType: String) {
        val resolver: ContentResolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, contentType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry for $fileName")
        try {
            resolver.openOutputStream(uri).use { outputStream ->
                outputStream?.write(bytes)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                showSavedMessage(getString(R.string.report_preview_saved_downloads, fileName))
            } else {
                showSavedMessage(getString(R.string.report_preview_saved_downloads_manual))
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // Clean up on failure
            Snackbar.make(content, getString(R.string.report_preview_save_failed, e.message.orEmpty()), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveToLegacyPrivateStorage(bytes: ByteArray, fileName: String, contentType: String) {
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val file = File(downloadsDir, fileName)
        try {
            FileOutputStream(file).use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                showSavedMessage(getString(R.string.report_preview_saved_opened))
            } else {
                showSavedMessage(getString(R.string.report_preview_saved_app_storage, fileName))
            }
        } catch (e: Exception) {
            Snackbar.make(content, getString(R.string.report_preview_save_failed, e.message.orEmpty()), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    // Donut ring chart for categorical / proportional distributions
    private inner class DonutDistributionChartView(
        context: Context,
        private val report: EventReportDto,
    ) : LinearLayout(context) {

        init {
            orientation = VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(CARD, 14, BORDER, density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(10)) }

            setupView()
        }

        private fun setupView() {
            val chart = chartDataOf(report, sortByValue = false)
            val filteredData = chart.entries.toMap(LinkedHashMap())
            val total = chart.total

            // Header row
            val headerRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val title = TextView(context).apply {
                text = getChartTitle(report)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT)
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(title)

            val totalPill = TextView(context).apply {
                text = getString(R.string.report_preview_chart_total, total)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(NAV_PURPLE)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = rounded(Color.parseColor("#EEF2FF"), 6, null, density = resources.displayMetrics.density)
            }
            headerRow.addView(totalPill)
            addView(headerRow)

            // Subtitle
            val subtitle = TextView(context).apply {
                text = getChartSubtitle(report)
                textSize = 12f
                setTextColor(MUTED)
                setPadding(0, dp(2), 0, dp(12))
            }
            addView(subtitle)

            if (total <= 0L || filteredData.isEmpty()) {
                val emptyTv = TextView(context).apply {
                    text = getString(emptyChartTextRes(report.reportType))
                    textSize = 13f
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, dp(24))
                }
                addView(emptyTv)
                return
            }

            // Donut Ring Canvas
            val donutRingView = DonutRingView(context, filteredData, total)
            addView(donutRingView)

            // Legend list
            val legendContainer = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(0, dp(12), 0, 0)
            }

            filteredData.entries.forEachIndexed { index, entry ->
                val sliceColor = getSliceColor(entry.key, index)
                val percent = (entry.value * 100.0 / total)

                val legendRow = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }

                // Color indicator dot
                val dot = View(context).apply {
                    layoutParams = LayoutParams(dp(10), dp(10)).apply {
                        marginEnd = dp(8)
                    }
                    background = rounded(sliceColor, 5, null, density = resources.displayMetrics.density)
                }
                legendRow.addView(dot)

                // Category Name
                val label = TextView(context).apply {
                    text = entry.key
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                legendRow.addView(label)

                // Count
                val count = TextView(context).apply {
                    text = entry.value.toString()
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT)
                }
                legendRow.addView(count)

                // Percentage
                val pct = TextView(context).apply {
                    text = String.format(Locale.ENGLISH, " (%.1f%%)", percent)
                    textSize = 12f
                    setTextColor(MUTED)
                    layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(4)
                    }
                }
                legendRow.addView(pct)

                legendContainer.addView(legendRow)
            }

            addView(legendContainer)
        }
    }

    // Donut ring view canvas
    private inner class DonutRingView(
        context: Context,
        private val data: Map<String, Long>,
        private val total: Long,
    ) : View(context) {

        private val arcPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }

        private val centerValPaint = Paint().apply {
            isAntiAlias = true
            color = TEXT
            textSize = dp(20).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        private val centerLblPaint = Paint().apply {
            isAntiAlias = true
            color = MUTED
            textSize = dp(10).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, dp(150))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val strokeWidth = dp(20).toFloat()
            arcPaint.strokeWidth = strokeWidth

            val radius = (min(cx, cy) - strokeWidth / 2f - dp(6).toFloat()).coerceAtLeast(dp(20).toFloat())
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            if (total <= 0L || data.isEmpty()) {
                arcPaint.color = Color.parseColor("#E5E7EB")
                canvas.drawOval(rect, arcPaint)
                val textY = cy - ((centerValPaint.descent() + centerValPaint.ascent()) / 2f) - dp(8).toFloat()
                canvas.drawText("0", cx, textY, centerValPaint)
                canvas.drawText(getString(R.string.report_preview_total_caps), cx, textY + dp(16).toFloat(), centerLblPaint)
                return
            }

            var startAngle = -90f
            val entries = data.entries.toList()
            val hasMultiple = entries.size > 1
            val gapAngle = if (hasMultiple) 2.5f else 0f

            for (i in entries.indices) {
                val entry = entries[i]
                val sweep = (entry.value.toFloat() / total.toFloat()) * 360f
                arcPaint.color = getSliceColor(entry.key, i)

                if (hasMultiple && sweep > gapAngle) {
                    canvas.drawArc(rect, startAngle + gapAngle / 2f, sweep - gapAngle, false, arcPaint)
                } else {
                    canvas.drawArc(rect, startAngle, sweep, false, arcPaint)
                }
                startAngle += sweep
            }

            // Center total text
            val textY = cy - ((centerValPaint.descent() + centerValPaint.ascent()) / 2f) - dp(8).toFloat()
            canvas.drawText(total.toString(), cx, textY, centerValPaint)
            canvas.drawText(getString(R.string.report_preview_total_caps), cx, textY + dp(16).toFloat(), centerLblPaint)
        }
    }

    // Horizontal ranked bar chart for entity frequencies (attendance, booths, points)
    private inner class HorizontalRankedBarChartView(
        context: Context,
        private val report: EventReportDto,
    ) : LinearLayout(context) {

        init {
            orientation = VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(CARD, 14, BORDER, density = resources.displayMetrics.density)
            elevation = dp(2).toFloat()
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(10)) }

            setupView()
        }

        private fun setupView() {
            val chart = chartDataOf(report, sortByValue = true)
            val filteredData = chart.entries.map { java.util.AbstractMap.SimpleEntry(it.first, it.second) }

            val maxValue = filteredData.maxOfOrNull { it.value } ?: 1L
            val total = chart.total

            // Header row
            val headerRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val title = TextView(context).apply {
                text = getChartTitle(report)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT)
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(title)

            val countPill = TextView(context).apply {
                text = getString(R.string.report_preview_items_count, filteredData.size)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(NAV_PURPLE)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = rounded(Color.parseColor("#EEF2FF"), 6, null, density = resources.displayMetrics.density)
            }
            headerRow.addView(countPill)
            addView(headerRow)

            // Subtitle
            val subtitle = TextView(context).apply {
                text = getChartSubtitle(report)
                textSize = 12f
                setTextColor(MUTED)
                setPadding(0, dp(2), 0, dp(12))
            }
            addView(subtitle)

            if (filteredData.isEmpty() || total <= 0L) {
                val emptyTv = TextView(context).apply {
                    text = getString(R.string.report_preview_no_activity)
                    textSize = 13f
                    setTextColor(MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, dp(24))
                }
                addView(emptyTv)
                return
            }

            // Ranked Rows
            filteredData.forEachIndexed { index, entry ->
                val fraction = if (maxValue > 0) (entry.value.toFloat() / maxValue.toFloat()) else 0f
                val percent = if (total > 0) (entry.value * 100.0 / total).roundToInt() else 0

                val itemContainer = LinearLayout(context).apply {
                    orientation = VERTICAL
                    layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 0, 0, dp(12))
                    }
                }

                // Top Row: Rank Badge + Label + Value (Pct)
                val infoRow = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }

                val rankBadge = TextView(context).apply {
                    text = "#${index + 1}"
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(NAV_PURPLE)
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(dp(22), dp(20)).apply {
                        marginEnd = dp(8)
                    }
                    background = rounded(Color.parseColor("#EEF2FF"), 4, null, density = resources.displayMetrics.density)
                }
                infoRow.addView(rankBadge)

                val nameLabel = TextView(context).apply {
                    text = entry.key
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                infoRow.addView(nameLabel)

                val valueLabel = TextView(context).apply {
                    text = "${entry.value} ($percent%)"
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT)
                    layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(8)
                    }
                }
                infoRow.addView(valueLabel)
                itemContainer.addView(infoRow)

                // Bar Track
                val barColor = when (index) {
                    0 -> Color.parseColor("#4F46E5")
                    1 -> Color.parseColor("#6366F1")
                    2 -> Color.parseColor("#8B5CF6")
                    else -> Color.parseColor("#A5B4FC")
                }
                val barView = RankedProgressBar(context, fraction, barColor)
                itemContainer.addView(barView)

                addView(itemContainer)
            }
        }
    }

    // Horizontal progress bar for ranked items
    private inner class RankedProgressBar(
        context: Context,
        private val fraction: Float,
        private val barColor: Int,
    ) : View(context) {

        private val trackPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F3F4F6")
            style = Paint.Style.FILL
        }

        private val barPaint = Paint().apply {
            isAntiAlias = true
            color = barColor
            style = Paint.Style.FILL
        }

        init {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply {
                topMargin = dp(6)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, dp(8))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = h / 2f

            // Draw track
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackPaint)

            // Draw progress
            if (fraction > 0f) {
                val fillWidth = (w * fraction.coerceIn(0f, 1f)).coerceAtLeast(h)
                canvas.drawRoundRect(0f, 0f, fillWidth, h, radius, radius, barPaint)
            }
        }
    }
}