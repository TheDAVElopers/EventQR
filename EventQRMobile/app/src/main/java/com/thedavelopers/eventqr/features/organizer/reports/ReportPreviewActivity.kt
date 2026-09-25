package com.thedavelopers.eventqr.features.organizer.reports

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportEmptyState
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportRowDto
import androidx.annotation.RequiresApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ReportPreviewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_REPORT = "report"
        private const val EXTRA_REPORTS = "reports"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_SOURCE_FILTERS = "source_filters"
        private const val EXTRA_IS_COMBINED = "is_combined"

        fun newSingleIntent(
            context: Context,
            eventId: String,
            report: EventReportDto,
            summary: EventReportSummaryDto,
            sourceFilters: EventReportFiltersDto,
        ): Intent {
            return Intent(context, ReportPreviewActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_REPORT, report)
                putExtra(EXTRA_SUMMARY, summary)
                putExtra(EXTRA_SOURCE_FILTERS, sourceFilters)
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
                putExtra(EXTRA_REPORTS, reports.toTypedArray())
                putExtra(EXTRA_SUMMARY, summary)
                putExtra(EXTRA_IS_COMBINED, true)
            }
        }
    }

    private lateinit var repository: OrganizerReportsRepository
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
        repository = OrganizerReportsRepository(this)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return finishWithError("Event ID is missing")
        isCombined = intent.getBooleanExtra(EXTRA_IS_COMBINED, false)
        summary = intent.getSerializableExtra(EXTRA_SUMMARY) as? EventReportSummaryDto ?: EventReportSummaryDto()

        if (isCombined) {
            val array = intent.getSerializableExtra(EXTRA_REPORTS) as? Array<EventReportDto>
            combinedReports = array?.toList() ?: emptyList()
        } else {
            singleReport = intent.getSerializableExtra(EXTRA_REPORT) as? EventReportDto
            sourceFilters = intent.getSerializableExtra(EXTRA_SOURCE_FILTERS) as? EventReportFiltersDto ?: EventReportFiltersDto()
        }

        if (!isCombined && singleReport == null) {
            return finishWithError("Report data is missing")
        }

        val reportTitle = if (isCombined) "Combined Report" else (singleReport?.reportTitle ?: "Report")
        content = organizerShell(
            title = "Report Preview",
            selectedNav = NAV_REPORTS,
            showBack = true,
            topRightLabel = "Export",
            onTopRight = { showExportDialog() },
        )

        render()
    }

    private fun render() {
        content.removeAllViews()

        // Report header info
        content.addView(card(16).apply {
            val report = singleReport ?: combinedReports?.firstOrNull()
            addView(text(report?.reportTitle ?: "Report", 20, true))
            addView(text(summary.eventName ?: "", 14, false, MUTED).apply { setPadding(0, dp(4), 0, dp(4)) })
            val generatedText = report?.generatedAtInstant?.let { "Generated ${dateFormatter.format(it)}" } ?: "Generated just now"
            addView(text(generatedText, 12, false, MUTED))
            if (!isCombined) {
                addView(buildFilterChips(sourceFilters).apply { setPadding(0, dp(8), 0, 0) })
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
            val start = filters.startDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: "Start"
            val end = filters.endDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: "End"
            addView(chip("Date: $start – $end", false, PRIMARY))
        }
        if (filters.attendeeQuery?.isNotBlank() == true) {
            addView(chip("Attendee: ${filters.attendeeQuery}", false, PRIMARY))
        }
        if (filters.status != EventReportFilterStatus.ALL) {
            addView(chip("Status: ${filters.status.name}", false, PRIMARY))
        }
        if (filters.startDate == null && filters.endDate == null && filters.attendeeQuery.isNullOrBlank() && filters.status == EventReportFilterStatus.ALL) {
            addView(chip("No filters applied", false, MUTED))
        }
    }

    private fun renderReportSection(report: EventReportDto) {
        // Chart
        if (report.chartSeries.isNotEmpty()) {
            content.addView(card(16).apply {
                addView(text("Chart Summary", 16, true).apply { setPadding(0, 0, 0, dp(8)) })
                addView(BarChartView(this@ReportPreviewActivity, report.chartSeries.filterKeys { it != null }))
            })
        }

        // Empty states
        when (report.emptyState) {
            EventReportEmptyState.NO_FILTER_MATCH -> {
                content.addView(emptyState(
                    iconRes = R.drawable.ic_organizer_reports,
                    title = "No matching records",
                    subtext = "No records matched your filters. Try adjusting your criteria.",
                    actionLabel = "Back to Filters",
                    onAction = { finish() },
                ))
            }
            EventReportEmptyState.NO_EVENT_RECORDS -> {
                content.addView(emptyState(
                    iconRes = R.drawable.ic_organizer_reports,
                    title = "No records yet",
                    subtext = "No records exist for this event yet. Data will appear as attendees interact.",
                ))
            }
            else -> {
                // Data table - use RecyclerView for pagination
                if (report.rows.isNotEmpty()) {
                    content.addView(buildPaginatedDataTable(report))
                } else {
                    content.addView(emptyState(
                        iconRes = R.drawable.ic_organizer_reports,
                        title = "No data to display",
                        subtext = "There is no data available for this report.",
                    ))
                }
            }
        }
    }

    private fun buildPaginatedDataTable(report: EventReportDto): LinearLayout {
        return card(12).apply {
            // Header
            addView(text(report.reportTitle ?: "", 16, true).apply { setPadding(0, 0, 0, dp(12)) })

            val columnCount = report.columns.size

            // Header row
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#F9FAFB"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                report.columns.forEachIndexed { index, column ->
                    addView(text(column ?: "", 13, true, TEXT).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                    })
                }
            })

            // RecyclerView for rows
            val recyclerView = RecyclerView(this@ReportPreviewActivity).apply {
                layoutManager = LinearLayoutManager(this@ReportPreviewActivity)
                adapter = ReportRowAdapter(report.rows, columnCount)
                setHasFixedSize(true)
            }
            addView(recyclerView)

            // Load more footer (optional - we can add later if needed)
            // For now, we'll just load all rows since reports are typically not huge
            // But we have the infrastructure ready if needed
        }
    }

    private inner class ReportRowAdapter(
        private val rows: List<EventReportRowDto>,
        private val columnCount: Int
    ) : RecyclerView.Adapter<ReportRowAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val rowContainer: LinearLayout = itemView as LinearLayout
            val views: MutableList<TextView> = mutableListOf()

            fun setupViews() {
                rowContainer.removeAllViews()
                views.clear()
                for (i in 0 until columnCount) {
                    val tv = TextView(rowContainer.context).apply {
                        setTextSize(13f)
                        setIncludeFontPadding(false)
                        setMaxLines(2)
                        setEllipsize(android.text.TextUtils.TruncateAt.END)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            gravity = Gravity.CENTER
                        }
                    }
                    views.add(tv)
                    rowContainer.addView(tv)
                }
            }

            fun bind(rowIndex: Int) {
                val row = rows[rowIndex]
                if (rowIndex % 2 == 0) {
                    rowContainer.setBackgroundColor(Color.parseColor("#F9FAFB"))
                } else {
                    rowContainer.setBackgroundColor(Color.WHITE)
                }

                for (i in views.indices) {
                    val value = if (i < row.values.size) row.values[i] else null
                    val displayValue = value?.ifBlank { "—" } ?: "—"
                    views[i].text = displayValue
                    views[i].setTextColor(if (i < row.values.size && value != null) Color.parseColor("#111827") else Color.parseColor("#6B7280"))
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
        if (isCombined) {
            Snackbar.make(rootView, "Export is unavailable for combined reports. Export each report individually.", Snackbar.LENGTH_LONG).show()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Export Report")
            .setMessage("Choose export format:")
            .setPositiveButton("CSV") { _, _ -> exportReport("CSV") }
            .setNegativeButton("PDF") { _, _ -> exportReport("PDF") }
            .setNeutralButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun exportReport(format: String) {
        val rootView = content.rootView ?: content
        Snackbar.make(rootView, "Preparing $format export...", Snackbar.LENGTH_SHORT).show()

        MainScope().launch {
            val filters = if (isCombined) {
                EventReportFiltersDto() // Default filters for combined
            } else {
                sourceFilters
            }
            val reportType = singleReport?.reportType ?: EventReportType.ROSTER

            when (val result = repository.exportReport(eventId, reportType, format, filters)) {
                is NetworkResult.Success -> {
                    saveAndShareFile(result.data.bytes, result.data.fileName, result.data.contentType)
                }
                is NetworkResult.Error -> {
                    Snackbar.make(rootView, "Export failed: ${result.message}", Snackbar.LENGTH_LONG)
                        .setAction("Retry") { exportReport(format) }
                        .show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun saveAndShareFile(bytes: ByteArray, fileName: String, contentType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToPublicDownloads(bytes, fileName, contentType)
        } else {
            saveToLegacyPrivateStorage(bytes, fileName, contentType)
        }
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
                Snackbar.make(content, "Export saved to Downloads/$fileName", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(content, "Export saved to Downloads (open manually)", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // Clean up on failure
            Snackbar.make(content, "Failed to save export: ${e.message}", Snackbar.LENGTH_LONG).show()
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
                Snackbar.make(content, "Export saved and opened (app storage)", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(content, "Export saved to app storage/$fileName", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Snackbar.make(content, "Failed to save export: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    // Simple bar chart view
    private class BarChartView(context: Context, private val data: Map<String?, Long>) : View(context) {
        private val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = PURPLE
            style = android.graphics.Paint.Style.FILL
            textSize = dp(12).toFloat()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        private val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = TEXT
            textSize = dp(11).toFloat()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        private val valuePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = PURPLE
            textSize = dp(11).toFloat()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val filteredData = data.filterKeys { it != null }
        private val maxValue = filteredData.values.maxOrNull() ?: 1L
        private val barColor = PURPLE

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val padding = dp(16).toFloat()
            val barAreaWidth = w - 2 * padding
            val barAreaHeight = h - 2 * padding - dp(40).toFloat()
            val entries = filteredData.entries.toList()
            val entryCount = entries.size
            val barWidth = (barAreaWidth / (entryCount * 1.5f)).coerceAtMost(dp(60).toFloat())
            val spacing = (barAreaWidth - barWidth * entryCount) / (entryCount + 1)

            for (i in entries.indices) {
                val entry = entries[i]
                val value = entry.value.toFloat()
                val barHeight = (value / maxValue.toFloat()) * barAreaHeight
                val x = padding + spacing + i * (barWidth + spacing)
                val y = padding + barAreaHeight - barHeight

                paint.color = barColor
                canvas.drawRect(x, y, x + barWidth, padding + barAreaHeight, paint)

                canvas.drawText(entry.value.toString(), x + barWidth / 2, y - dp(4).toFloat(), valuePaint)
                canvas.drawText(entry.key ?: "", x + barWidth / 2, h - padding + dp(4).toFloat(), labelPaint)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                dp(200)
            )
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    }
}