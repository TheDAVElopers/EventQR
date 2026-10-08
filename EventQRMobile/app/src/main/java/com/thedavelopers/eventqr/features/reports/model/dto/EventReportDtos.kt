package com.thedavelopers.eventqr.features.reports.model.dto

import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class EventReportType : Serializable {
    ROSTER,
    NO_SHOWS,
    ENTRY_LOGS,
    ATTENDANCE,
    CLAIMS,
    BOOTH_VISITS,
    EXIT_LOGS,
    POINTS,
}

enum class EventReportFilterStatus : Serializable {
    ALL,
    APPROVED,
    REJECTED,
}

enum class EventReportEmptyState : Serializable {
    NONE,
    NO_FILTER_MATCH,
    NO_EVENT_RECORDS,
}

data class EventReportSummaryDto(
    val eventId: String? = "",
    val eventName: String? = "",
    val registeredCount: Int = 0,
    val checkedInCount: Int = 0,
    val exitedCount: Int = 0,
    val hasAnyRecords: Boolean = false,
) : Serializable

data class EventReportFiltersDto(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val attendeeQuery: String? = null,
    val status: EventReportFilterStatus = EventReportFilterStatus.ALL,
) : Serializable

data class EventReportRowDto(
    val values: List<String?> = emptyList(),
) : Serializable

data class EventReportDto(
    val eventId: String? = "",
    val reportType: EventReportType = EventReportType.ROSTER,
    val reportTitle: String? = "",
    val eventName: String? = "",
    val generatedAt: String? = null,
    val columns: List<String?> = emptyList(),
    val rows: List<EventReportRowDto> = emptyList(),
    val chartSeries: Map<String?, Long> = emptyMap(),
    /** Full total across every category (including the "Other" bucket); 0 from older servers. */
    val total: Long = 0L,
    val emptyState: EventReportEmptyState = EventReportEmptyState.NONE,
    val appliedFilters: EventReportFiltersDto = EventReportFiltersDto(),
) : Serializable {

    val generatedAtInstant: Instant?
        get() = generatedAt?.let { Instant.parse(it) }
}

data class EventReportExportRequestDto(
    val format: String = "CSV",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val attendeeQuery: String? = null,
    val status: EventReportFilterStatus = EventReportFilterStatus.ALL,
)

data class EventReportCatalogItem(
    val reportType: EventReportType,
    val label: String,
    val iconRes: Int,
    val iconTint: Int,
    val iconBg: Int,
) : Serializable
