package com.thedavelopers.eventqr.features.organizer.reports

import androidx.annotation.ColorRes
import android.content.res.Resources
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import java.time.LocalDate

/** Pure rules for the report filter sheet. Every filter is optional; only an inverted date range blocks Generate. */
object ReportFilterRules {
    fun isRangeInverted(start: LocalDate?, end: LocalDate?): Boolean =
        start != null && end != null && end.isBefore(start)

    fun canGenerate(start: LocalDate?, end: LocalDate?): Boolean = !isRangeInverted(start, end)

    /** "Skip date filters": drops only the dates and keeps the attendee and status filters. */
    fun clearDates(filters: EventReportFiltersDto): EventReportFiltersDto =
        filters.copy(startDate = null, endDate = null)
}

enum class PointsSign { POSITIVE, NEGATIVE, ZERO }

data class PointsCell(val text: String, val sign: PointsSign)

/** Renders the signed `pointsChanged` value of the Points report table: "+5", "-10" (true minus), "0". */
fun formatPointsCell(raw: String?): PointsCell {
    val trimmed = raw?.trim().orEmpty()
    val value = trimmed.toLongOrNull() ?: return PointsCell(trimmed.ifBlank { "—" }, PointsSign.ZERO)
    return when {
        value > 0 -> PointsCell("+$value", PointsSign.POSITIVE)
        value < 0 -> PointsCell("−${-value}", PointsSign.NEGATIVE)
        else -> PointsCell("0", PointsSign.ZERO)
    }
}

/** Table header for the Points report points column; older servers still send "Points Earned". */
fun displayColumnName(reportType: EventReportType, index: Int, serverName: String?): String {
    val name = serverName.orEmpty()
    return if (reportType == EventReportType.POINTS && index == 1 && name.equals("Points Earned", ignoreCase = true)) {
        "Points (+/-)"
    } else {
        name
    }
}

const val OTHER_CHART_LABEL = "Other"

/** Chart data as drawn: server order, "Other" always last, total taken from the server when provided. */
data class ChartData(val entries: List<Pair<String, Long>>, val total: Long) {
    val hasOther: Boolean get() = entries.any { it.first == OTHER_CHART_LABEL }
}

/**
 * Sorted charts rank every entry by value, "Other" included, and only fall back to placing "Other"
 * last when two values tie: a forced rank 6 hides a real bucket that outranks the rest.
 */
fun chartDataOf(report: EventReportDto, sortByValue: Boolean): ChartData {
    val all = report.chartSeries.entries
        .mapNotNull { (k, v) -> k?.let { it to v } }
        .filter { it.second >= 0 }
    val entries = if (sortByValue) {
        all.sortedWith(
            compareByDescending<Pair<String, Long>> { it.second }
                .thenBy { if (it.first == OTHER_CHART_LABEL) 1 else 0 }
                .thenBy { it.first },
        )
    } else {
        all
    }
    val sum = entries.sumOf { it.second }
    return ChartData(entries, if (report.total > 0L) report.total else sum)
}

/**
 * Chart colour per exact category (§3.2), not per substring: the old matcher gave "Registered" and
 * "Entered" the same tone, and a five-status roster must read as five distinct spans.
 *
 * Roster carries five statuses, so the two that pair with the chart ramp by meaning (cancelled,
 * no show) take the error and warning tones; every value still clears 3:1 on surface. Ranked
 * reports (attendance, booth visits, points) are one series, so their bars share chart_1 and the
 * row label carries identity - a hue per rank is the eight-colour template tell the plan bans.
 *
 * A label this build does not know returns `chart_unmapped`, deliberately not `chart_other`: a
 * server-side rename then paints a loud blue span instead of silently folding a whole distribution
 * into the real Other bucket. `ReportPresentationTest` pins every label the backend can send.
 */
@ColorRes
fun reportChartColor(reportType: EventReportType, label: String): Int {
    if (label == OTHER_CHART_LABEL) return R.color.chart_other
    return when (reportType) {
        EventReportType.ROSTER -> when (label) {
            "Registered" -> R.color.chart_1
            "Entered" -> R.color.chart_2
            "Exited" -> R.color.chart_3
            "Cancelled" -> R.color.ui_error
            "No Show" -> R.color.ui_warning
            else -> unmappedLabel(label)
        }
        EventReportType.NO_SHOWS -> when (label) {
            "Marked No Show" -> R.color.ui_warning
            "Not Entered" -> R.color.chart_3
            else -> unmappedLabel(label)
        }
        EventReportType.ENTRY_LOGS,
        EventReportType.EXIT_LOGS,
        EventReportType.CLAIMS -> when (label) {
            "Approved" -> R.color.chart_1
            "Rejected" -> R.color.ui_error
            // prettyResult(null) on the server; a real label, not a reader mistake.
            "Unknown" -> R.color.chart_3
            else -> unmappedLabel(label)
        }
        EventReportType.ATTENDANCE,
        EventReportType.BOOTH_VISITS,
        EventReportType.POINTS -> R.color.chart_1
    }
}

private fun unmappedLabel(label: String): Int {
    android.util.Log.w("ReportChart", "No chart tone for \"$label\" - check EventReportGenerationService labels")
    return R.color.chart_unmapped
}

/**
 * The line under a 1-2 span split bar: both categories by name, so colour is never the only key.
 * One span prints count and name only; 3+ spans get legend rows instead, so they never reach here.
 */
internal fun splitLabelText(res: Resources, entries: List<Pair<String, Long>>): CharSequence {
    val first = entries.firstOrNull() ?: return ""
    val second = entries.getOrNull(1) ?: return res.getString(
        R.string.report_preview_split_label_single,
        first.second.toString(),
        first.first,
    )
    return res.getString(
        R.string.report_preview_split_label,
        first.second.toString(),
        first.first,
        second.second.toString(),
        second.first,
    )
}

/** Combined report on a phone: null when the section carries no value, so no "0 categories" line. */
internal fun combinedSummaryText(res: Resources, data: ChartData): CharSequence? {
    val count = data.entries.count { it.second > 0L }
    if (data.total <= 0L || count == 0) return null
    return res.getQuantityString(R.plurals.report_preview_combined_summary, count, count, data.total)
}
