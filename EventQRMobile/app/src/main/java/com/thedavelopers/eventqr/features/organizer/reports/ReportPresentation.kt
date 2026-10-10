package com.thedavelopers.eventqr.features.organizer.reports

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

fun chartDataOf(report: EventReportDto, sortByValue: Boolean): ChartData {
    val all = report.chartSeries.entries
        .mapNotNull { (k, v) -> k?.let { it to v } }
        .filter { it.second >= 0 }
    val other = all.filter { it.first == OTHER_CHART_LABEL }
    var main = all.filter { it.first != OTHER_CHART_LABEL }
    if (sortByValue) main = main.sortedByDescending { it.second }
    val entries = main + other
    val sum = entries.sumOf { it.second }
    return ChartData(entries, if (report.total > 0L) report.total else sum)
}
