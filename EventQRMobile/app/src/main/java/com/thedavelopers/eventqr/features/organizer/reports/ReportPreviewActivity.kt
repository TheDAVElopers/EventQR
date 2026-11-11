package com.thedavelopers.eventqr.features.organizer.reports

import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenPx
import androidx.lifecycle.lifecycleScope
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportEmptyState
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportRowDto
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Report preview (§6.4). The screen is an XML layout on the shared component set - shared top bar,
 * a page of `item_report_*` blocks, and the organizer bottom nav. Every container comes from XML;
 * Kotlin only fills cells and draws the two chart shapes. No `GradientDrawable`, no `roundedRect()`,
 * no nested cards, no tinted tiles.
 */
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
    private val dateFormatter = com.thedavelopers.eventqr.core.util.DateFormatters.displayFormatter
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

        setContentView(R.layout.activity_report_preview)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        content = findViewById(R.id.reportContent)
        bindTopBar()
        findViewById<LinearLayout>(R.id.bottomNavHost).addView(bottomNav(NAV_REPORTS))
        capContentWidth()

        render()
    }

    /**
     * Wide windows keep one readable column: `android:maxWidth` is only honoured by TextView and
     * ImageView, and a `match_parent` ScrollView child is stretched by `fillViewport` and by its
     * own layout params, so the cap is applied as an explicit width and the FrameLayout gravity
     * centres it. On a phone the available width is already below the cap, so nothing changes.
     */
    private fun capContentWidth() {
        findViewById<ScrollView>(R.id.reportScroll).doOnLayout { scroll ->
            val cap = dimenPx(R.dimen.ui_report_content_max_width)
            val available = scroll.width - scroll.paddingStart - scroll.paddingEnd
            if (available > cap) {
                content.layoutParams = content.layoutParams.apply { width = cap }
            }
        }
    }

    /** §5.8 top bar: title at x = 56dp, one trailing action, hairline below. */
    private fun bindTopBar() {
        val bar = findViewById<View>(R.id.topBar)
        bar.findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.report_preview_title)
        bar.findViewById<View>(R.id.topBarBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val export = TextView(this).apply {
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Label)
            setTextColor(color(R.color.ui_primary))
            text = getString(R.string.report_preview_export)
            gravity = Gravity.CENTER
            minWidth = dimenPx(R.dimen.ui_touch_min)
            minHeight = dimenPx(R.dimen.ui_touch_min)
            setPadding(dimenPx(R.dimen.space_12), 0, dimenPx(R.dimen.space_12), 0)
            setOnClickListener { showExportDialog() }
        }
        val row = bar.findViewById<LinearLayout>(R.id.topBar).getChildAt(0) as LinearLayout
        row.removeView(bar.findViewById(R.id.topBarAction))
        row.addView(
            export,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dimenPx(R.dimen.ui_touch_min),
            ).apply { marginEnd = dimenPx(R.dimen.space_8) },
        )
    }

    private fun render() {
        content.removeAllViews()

        val header = inflate(R.layout.item_report_header)
        val report = singleReport ?: combinedReports?.firstOrNull()
        header.findViewById<TextView>(R.id.txtReportTitle).text =
            if (isCombined) getString(R.string.report_preview_combined_title)
            else (report?.reportTitle ?: getString(R.string.report_preview_report))
        header.findViewById<TextView>(R.id.txtRecordCount).text =
            if (isCombined) getString(R.string.report_preview_reports_count, combinedReports?.size ?: 0)
            else getString(R.string.report_preview_records_count, report?.rows?.size ?: 0)
        header.findViewById<TextView>(R.id.txtReportEvent).apply {
            val name = summary.eventName
            text = name
            visibility = if (name.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        header.findViewById<TextView>(R.id.txtReportGenerated).apply {
            // Server generatedAt only; never invent a client-side time.
            val generatedAt = report?.generatedAtInstant
            text = generatedAt?.let { getString(R.string.report_preview_generated_at, dateFormatter.format(it)) }.orEmpty()
            visibility = if (generatedAt == null) View.GONE else View.VISIBLE
        }
        header.findViewById<TextView>(R.id.txtReportFilters).text =
            if (isCombined) getString(R.string.report_preview_combined_contains) else appliedFiltersText(resources, sourceFilters)
        content.addView(header)

        if (isCombined) {
            combinedReports?.forEach { renderReportSection(it) }
        } else {
            singleReport?.let { renderReportSection(it) }
        }
    }

    private fun inflate(layoutRes: Int): View = LayoutInflater.from(this).inflate(layoutRes, content, false)

    /** Combined report on a phone: no chart, one line that states the section's shape. */
    private fun combinedSummaryLine(report: EventReportDto): TextView? {
        val data = chartDataOf(report, sortByValue = false)
        val text = combinedSummaryText(resources, data) ?: return null
        return TextView(this).apply {
            this.text = text
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(color(R.color.ui_text_secondary))
            setPadding(0, dimenPx(R.dimen.space_12), 0, 0)
        }
    }

    /**
     * Applied filters as one provenance line (§6.4): count, then the values joined by " · ".
     * No filters -> "No filters applied". The line is text, never a chip: a bordered chip
     * silhouette with no tap handler is a false affordance.
     */
    private fun renderReportSection(report: EventReportDto) {
        if (isCombined) {
            val section = inflate(R.layout.item_report_section)
            section.findViewById<TextView>(R.id.txtSectionTitle).text =
                report.reportTitle ?: getString(R.string.report_preview_section)
            section.findViewById<TextView>(R.id.txtSectionCount).text =
                getString(R.string.report_preview_records_count, report.rows.size)
            content.addView(section)
        }

        if (report.chartSeries.isNotEmpty()) {
            if (isCombined && !resources.getBoolean(R.bool.report_combined_charts)) {
                combinedSummaryLine(report)?.let { content.addView(it) }
            } else {
                content.addView(buildChart(report))
            }
        }

        when (report.emptyState) {
            EventReportEmptyState.NO_FILTER_MATCH -> content.addView(
                emptyState(
                    iconRes = AppIcons.Report,
                    title = getString(R.string.report_preview_no_matching_title),
                    subtext = getString(R.string.report_preview_no_matching_subtext),
                    actionLabel = getString(R.string.report_preview_back_to_filters),
                    onAction = { finish() },
                ),
            )
            EventReportEmptyState.NO_EVENT_RECORDS -> content.addView(
                emptyState(
                    iconRes = AppIcons.Report,
                    title = getString(R.string.report_preview_no_records_title),
                    subtext = getString(R.string.report_preview_no_records_subtext),
                ),
            )
            else -> {
                if (report.rows.isNotEmpty()) {
                    content.addView(buildDataTable(report))
                } else {
                    content.addView(
                        emptyState(
                            iconRes = AppIcons.Report,
                            title = getString(R.string.report_preview_no_data_title),
                            subtext = getString(R.string.report_preview_no_data_subtext),
                        ),
                    )
                }
            }
        }
    }

    private fun isDistributionReport(report: EventReportDto): Boolean = when (report.reportType) {
        EventReportType.ROSTER,
        EventReportType.NO_SHOWS,
        EventReportType.ENTRY_LOGS,
        EventReportType.CLAIMS,
        EventReportType.EXIT_LOGS -> true
        EventReportType.ATTENDANCE,
        EventReportType.BOOTH_VISITS,
        EventReportType.POINTS -> false
    }

    private fun getChartTitle(report: EventReportDto): String = when (report.reportType) {
        EventReportType.ROSTER -> getString(R.string.report_preview_chart_title_roster)
        EventReportType.NO_SHOWS -> getString(R.string.report_preview_chart_title_no_shows)
        EventReportType.ENTRY_LOGS -> getString(R.string.report_preview_chart_title_entry_logs)
        EventReportType.ATTENDANCE -> getString(R.string.report_preview_chart_title_attendance)
        EventReportType.CLAIMS -> getString(R.string.report_preview_chart_title_claims)
        EventReportType.BOOTH_VISITS -> getString(R.string.report_preview_chart_title_booth_visits)
        EventReportType.EXIT_LOGS -> getString(R.string.report_preview_chart_title_exit_logs)
        EventReportType.POINTS -> getString(R.string.report_preview_chart_title_points)
    }

    private fun getChartSubtitle(report: EventReportDto): String = when (report.reportType) {
        EventReportType.ROSTER -> getString(R.string.report_preview_roster_chart_subtitle)
        EventReportType.NO_SHOWS -> getString(R.string.report_preview_chart_subtitle_no_shows)
        EventReportType.ENTRY_LOGS -> getString(R.string.report_preview_chart_subtitle_entry_logs)
        EventReportType.ATTENDANCE -> getString(R.string.report_preview_attendance_chart_subtitle)
        EventReportType.CLAIMS -> getString(R.string.report_preview_claims_chart_subtitle)
        EventReportType.BOOTH_VISITS -> getString(R.string.report_preview_booth_chart_subtitle)
        EventReportType.EXIT_LOGS -> getString(R.string.report_preview_chart_subtitle_exit_logs)
        EventReportType.POINTS -> getString(R.string.report_preview_chart_subtitle_points)
    }

    private fun getColumnWeight(index: Int, columnCount: Int, reportType: EventReportType): Float {
        return when (reportType) {
            EventReportType.CLAIMS -> when (index) {
                0 -> 1.0f
                1 -> 1.1f
                2 -> 1.1f
                else -> 0.8f
            }
            EventReportType.ATTENDANCE, EventReportType.BOOTH_VISITS -> when (index) {
                0 -> 1.0f
                1 -> 1.4f
                else -> 1.0f
            }
            EventReportType.POINTS -> when (index) {
                0 -> 1.0f
                1 -> 0.8f
                else -> 1.4f
            }
            EventReportType.ENTRY_LOGS, EventReportType.EXIT_LOGS -> when (index) {
                0 -> 1.1f
                1 -> 1.2f
                else -> 0.9f
            }
            EventReportType.ROSTER -> when (index) {
                0 -> 1.1f
                1 -> 0.9f
                else -> 1.1f
            }
            EventReportType.NO_SHOWS -> when (index) {
                0 -> 1.1f
                1 -> 1.0f
                else -> 1.0f
            }
        }
    }

    // ─────────────────────────── charts ───────────────────────────

    /**
     * One chart card per report: a 100% split bar for distributions (§6.4), ranked rows for the
     * activity reports. A combined report keeps its charts only on a tablet - on a phone the
     * section shows one summary line and the table, the same content the export carries.
     */
    private fun buildChart(report: EventReportDto): View {
        val card = inflate(R.layout.item_report_chart)
        val title = getChartTitle(report)
        val subtitle = getChartSubtitle(report)
        val titleView = card.findViewById<TextView>(R.id.txtChartTitle)
        val subtitleView = card.findViewById<TextView>(R.id.txtChartSubtitle)
        titleView.text = title
        subtitleView.text = subtitle
        val host = card.findViewById<FrameLayout>(R.id.chartHost)
        val legend = card.findViewById<LinearLayout>(R.id.chartLegend)
        val badge = card.findViewById<TextView>(R.id.txtChartTotal)
        val data = chartDataOf(report, sortByValue = !isDistributionReport(report))

        if (data.total <= 0L || data.entries.isEmpty()) {
            badge.visibility = View.GONE
            host.addView(chartEmptyText(emptyChartTextRes(report.reportType)))
            return card
        }

        val totalText = when {
            isDistributionReport(report) -> getString(R.string.report_preview_chart_total, data.total)
            report.reportType == EventReportType.POINTS ->
                resources.getQuantityString(R.plurals.report_preview_points_total, quantity(data.total), data.total)
            else -> getString(R.string.report_preview_items_count, data.entries.size)
        }
        badge.text = totalText

        if (isDistributionReport(report)) {
            host.addView(
                SplitBarView(
                    this,
                    data.entries.map {
                        SplitSegment(color(reportChartColor(report.reportType, it.first)), it.second)
                    },
                ),
            )
            if (data.entries.size > 2) {
                // Five statuses cannot be named on one line: one labelled row each, still no legend
                // chrome beyond the dot (5.5 row rhythm).
                data.entries.forEach { entry ->
                    legend.addView(
                        legendRow(
                            context = this,
                            label = entry.first,
                            valueText = entry.second.toString(),
                            percentText = percentText(entry.second, data.total),
                            sliceColor = color(reportChartColor(report.reportType, entry.first)),
                        ),
                    )
                }
            } else {
                legend.addView(splitLabelLine(data))
            }
        } else {
            host.addView(rankedRows(report.reportType, data))
        }

        // One node for the whole chart, so TalkBack never walks eight anonymous views and never
        // reads a value twice: the rows, title, subtitle and total all yield to this summary.
        titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        subtitleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        badge.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        card.contentDescription = chartSummary(title, subtitle, totalText, data, report.reportType)
        return card
    }

    private fun chartEmptyText(res: Int): TextView = TextView(this).apply {
        text = getString(res)
        setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
        setTextColor(color(R.color.ui_text_secondary))
        setPadding(0, dimenPx(R.dimen.space_24), 0, dimenPx(R.dimen.space_24))
    }

    private fun emptyChartTextRes(type: EventReportType): Int = when (type) {
        EventReportType.POINTS -> R.string.report_preview_empty_points
        EventReportType.ATTENDANCE -> R.string.report_preview_empty_attendance
        EventReportType.BOOTH_VISITS -> R.string.report_preview_empty_booth
        else -> R.string.report_preview_empty_chart
    }

    /** The one spoken summary of a chart: title, what it counts, the total, then every span. */
    private fun chartSummary(
        title: String,
        subtitle: String,
        totalText: String,
        data: ChartData,
        reportType: EventReportType,
    ): CharSequence {
        val parts = data.entries.joinToString(" · ") { (label, value) ->
            getString(
                R.string.report_preview_chart_segment_a11y,
                label,
                valueText(reportType, value),
                percentText(value, data.total),
            )
        }
        return getString(R.string.report_preview_chart_a11y, title, subtitle, totalText, parts)
    }

    /** Direct label under a two-series bar: both categories by name, so colour is never the only key. */
    private fun splitLabelLine(data: ChartData): TextView {
        return TextView(this).apply {
            text = splitLabelText(resources, data.entries)
            setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
            setTextColor(color(R.color.ui_text_secondary))
            maxLines = 2
            setPadding(0, dimenPx(R.dimen.space_8), 0, 0)
            // The card's summary already names both spans; this is the visible copy only.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun rankedRows(reportType: EventReportType, data: ChartData): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val maxValue = data.entries.maxOfOrNull { it.second } ?: 1L
        data.entries.forEachIndexed { index, entry ->
            val percent = percentText(entry.second, data.total)
            val value = valueText(reportType, entry.second)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                // Top aligned so a two-line label grows the row instead of pushing the value down.
                gravity = Gravity.TOP
                setPadding(0, dimenPx(R.dimen.ui_row_padding_vertical), 0, 0)
                // The card's summary names every rank, so this row is decoration to TalkBack.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            info.addView(TextView(this).apply {
                text = (index + 1).toString()
                setTextAppearance(R.style.TextAppearance_EventQR_Type_NumericS)
                setTextColor(color(R.color.ui_text_secondary))
                minWidth = dimenPx(R.dimen.space_24)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            info.addView(TextView(this).apply {
                text = entry.first
                setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
                setTextColor(color(R.color.ui_text_primary))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dimenPx(R.dimen.space_8), 0, dimenPx(R.dimen.space_8), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            info.addView(TextView(this).apply {
                text = getString(R.string.report_preview_ranked_value, value, percent)
                setTextAppearance(R.style.TextAppearance_EventQR_Type_Micro)
                setTextColor(color(R.color.ui_text_secondary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            container.addView(info)
            container.addView(
                RankedBarView(
                    context = this,
                    fraction = if (maxValue > 0) entry.second.toFloat() / maxValue.toFloat() else 0f,
                    barColor = color(reportChartColor(reportType, entry.first)),
                ),
            )
        }
        return container
    }

    /** Ranked values are counts except on Points, where the series sums awarded points. */
    private fun valueText(reportType: EventReportType, value: Long): String =
        if (reportType == EventReportType.POINTS) {
            resources.getQuantityString(R.plurals.report_preview_points_value, quantity(value), value)
        } else {
            value.toString()
        }

    /** One decimal everywhere: a whole percent prints "0%" for a real share and disagrees with a legend. */
    private fun percentText(value: Long, total: Long): String =
        if (total > 0) String.format(Locale.ENGLISH, "%.1f", value * 100.0 / total) else "0.0"

    /** Plurals take an Int quantity; counts and points never approach the ceiling, but clamp anyway. */
    private fun quantity(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    // ─────────────────────────── table ───────────────────────────

    private fun buildDataTable(report: EventReportDto): View {
        val card = inflate(R.layout.item_report_table)
        card.findViewById<TextView>(R.id.txtTableCount).text =
            getString(R.string.report_preview_records_count, report.rows.size)

        val header = card.findViewById<LinearLayout>(R.id.tableHeader)
        val columnCount = report.columns.size
        val isFourCol = columnCount >= 4
        report.columns.forEachIndexed { index, column ->
            header.addView(TextView(this).apply {
                text = displayColumnName(report.reportType, index, column)
                setTextAppearance(if (isFourCol) R.style.TextAppearance_EventQR_Type_Overline else R.style.TextAppearance_EventQR_Type_Label)
                setTextColor(color(R.color.ui_text_secondary))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(0, dimenPx(R.dimen.space_8), dimenPx(R.dimen.space_8), dimenPx(R.dimen.space_8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, getColumnWeight(index, columnCount, report.reportType))
            })
        }

        val rows = card.findViewById<RecyclerView>(R.id.tableRows)
        rows.layoutManager = LinearLayoutManager(this)
        rows.adapter = ReportRowAdapter(report)
        rows.isNestedScrollingEnabled = false
        return card
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
                        setTextAppearance(if (isFourCol) R.style.TextAppearance_EventQR_Type_Overline else R.style.TextAppearance_EventQR_Type_Label)
                        includeFontPadding = false
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        setPadding(0, 0, dimenPx(R.dimen.space_8), 0)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, getColumnWeight(i, columnCount, report.reportType))
                    }
                    views.add(tv)
                    rowContainer.addView(tv)
                }
            }

            fun bind(rowIndex: Int) {
                val row = rows[rowIndex]
                for (i in views.indices) {
                    val value = if (i < row.values.size) row.values[i] else null
                    val isPointsCell = report.reportType == EventReportType.POINTS && i == 1
                    val pointsCell = if (isPointsCell) formatPointsCell(value) else null
                    val displayValue = pointsCell?.text ?: (value?.ifBlank { "—" } ?: "—")
                    views[i].text = displayValue

                    val lower = displayValue.lowercase()
                    val cellColor = when {
                        pointsCell?.sign == PointsSign.NEGATIVE -> color(R.color.ui_error)
                        pointsCell?.sign == PointsSign.POSITIVE -> color(R.color.ui_success)
                        lower == "success" || lower == "entered" || lower == "completed" -> color(R.color.ui_success)
                        lower == "no show" || lower == "marked no show" || lower == "invalid" || lower == "failed" -> color(R.color.ui_error)
                        lower == "duplicate" || lower == "already claimed" || lower == "not entered" -> color(R.color.ui_on_warning_container)
                        else -> color(R.color.ui_text_primary)
                    }
                    views[i].setTextColor(cellColor)
                    views[i].setTypeface(
                        if (i == 0) androidx.core.content.res.ResourcesCompat.getFont(this@ReportPreviewActivity, R.font.ibm_plex_sans_semibold) else null,
                    )
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setPadding(0, dimenPx(R.dimen.ui_table_row_pad_v), 0, dimenPx(R.dimen.ui_table_row_pad_v))
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

    // ─────────────────────────── export (behaviour unchanged) ───────────────────────────

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
        return ReportExporter(
            labels,
            eventName,
            dateFormatter.format(combinedGeneratedAt(reports)),
            ReportExportPalette(
                brandDeepest = color(R.color.ui_text_primary),
                primary = color(R.color.ui_primary),
                brandWash = color(R.color.ui_primary_container),
                ink700 = color(R.color.ui_text_primary),
                textSecondary = color(R.color.ui_text_secondary),
                textPrimary = color(R.color.ui_text_primary),
                sunken = color(R.color.ui_surface_sunken),
                zebra = color(R.color.ui_surface_pressed),
                hairline = color(R.color.ui_hairline),
            ),
        )
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
}


/**
 * The one drawable the chart legend dot may use (§6.4). A tint on `bg_ui_dot` inherits its 60%
 * alpha and paints a pale dot beside a full-strength bar, so the legend and
 * `ReportLegendDotTest` both resolve through this reference: reverting the legend to `bg_ui_dot`
 * fails the alpha assertion instead of passing on an unused resource.
 */
internal val legendDotDrawable = R.drawable.bg_ui_dot_solid

/**
 * §6.4 list-row rhythm: dot, label, count and share; the dot sits 8dp from the label.
 *
 * File-level and `internal` so `ReportLegendDotTest` builds the real row and paints its first
 * child: the opaque-dot guard then covers the call site, not just the drawable resource.
 */
internal fun legendRow(
    context: Context,
    label: String,
    valueText: String,
    percentText: String,
    sliceColor: Int,
): View {
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            0,
            context.dimenPx(R.dimen.ui_row_padding_vertical),
            0,
            context.dimenPx(R.dimen.ui_row_padding_vertical),
        )
        // The card's summary names every span, so this row is decoration to TalkBack.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    row.addView(View(context).apply {
        // Opaque dot: a tint on bg_ui_dot would inherit its 60% alpha and read as a pale span.
        background = ContextCompat.getDrawable(context, legendDotDrawable)?.mutate()
        backgroundTintList = ColorStateList.valueOf(sliceColor)
        layoutParams = LinearLayout.LayoutParams(
            context.dimenPx(R.dimen.ui_badge_dot),
            context.dimenPx(R.dimen.ui_badge_dot),
        ).apply {
            marginEnd = context.dimenPx(R.dimen.space_8)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })
    row.addView(TextView(context).apply {
        text = label
        setTextAppearance(R.style.TextAppearance_EventQR_Type_BodySmall)
        setTextColor(context.color(R.color.ui_text_primary))
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })
    row.addView(TextView(context).apply {
        text = valueText
        setTextAppearance(R.style.TextAppearance_EventQR_Type_NumericS)
        setTextColor(context.color(R.color.ui_text_primary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })
    row.addView(TextView(context).apply {
        text = percentText
        setTextAppearance(R.style.TextAppearance_EventQR_Type_Micro)
        setTextColor(context.color(R.color.ui_text_secondary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })
    return row
}

/**
 * Applied filters as provenance (§6.4). Counted 1..3: a date span is one filter, not two.
 *
 * The line is text, never a chip: a bordered chip silhouette with no tap handler is a false
 * affordance. Dates come from the shared [DateFormatters] vocabulary, not a local pattern.
 */
internal fun appliedFiltersText(resources: Resources, filters: EventReportFiltersDto): String {
    val datePattern = DateFormatters.eventDateFormatter
    val values = buildList {
        if (filters.startDate != null || filters.endDate != null) {
            val start = filters.startDate?.format(datePattern) ?: resources.getString(R.string.report_preview_filter_start)
            val end = filters.endDate?.format(datePattern) ?: resources.getString(R.string.report_preview_filter_end)
            add(resources.getString(R.string.report_preview_filter_date, start, end))
        }
        filters.attendeeQuery?.takeIf { it.isNotBlank() }?.let {
            add(resources.getString(R.string.report_preview_filter_attendee, it))
        }
        if (filters.status != EventReportFilterStatus.ALL) {
            add(resources.getString(R.string.report_preview_filter_status, filters.status.name))
        }
    }
    if (values.isEmpty()) return resources.getString(R.string.report_preview_filters_none)
    val count = resources.getQuantityString(R.plurals.report_preview_filters_count, values.size, values.size)
    return "$count \u00B7 ${values.joinToString(" \u00B7 ")}"
}
