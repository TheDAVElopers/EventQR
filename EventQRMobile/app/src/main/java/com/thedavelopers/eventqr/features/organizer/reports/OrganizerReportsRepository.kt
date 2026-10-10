package com.thedavelopers.eventqr.features.organizer.reports

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType

class OrganizerReportsRepository(private val context: Context) {
    private val apiService = ApiClient.getService(context)

    suspend fun fetchSummary(eventId: String): NetworkResult<EventReportSummaryDto> =
        safeApiCall { apiService.getEventReportSummary(eventId) }

    suspend fun generateReport(
        eventId: String,
        reportType: EventReportType,
        filters: EventReportFiltersDto,
    ): NetworkResult<EventReportDto> = safeApiCall {
        apiService.getEventReportByType(
            eventId = eventId,
            reportType = reportType,
            startDate = filters.startDate?.toString(),
            endDate = filters.endDate?.toString(),
            attendeeQuery = filters.attendeeQuery?.trim()?.takeIf { it.isNotBlank() },
            status = filters.status,
        )
    }

    companion object {
        val transactionStatusApplicable: Set<EventReportType> = setOf(
            EventReportType.ENTRY_LOGS,
            EventReportType.ATTENDANCE,
            EventReportType.CLAIMS,
            EventReportType.BOOTH_VISITS,
        )

        val attendeeQueryApplicable: Set<EventReportType> = setOf(
            EventReportType.ROSTER,
            EventReportType.ATTENDANCE,
            EventReportType.POINTS,
        )

        fun defaultFilters(): EventReportFiltersDto = EventReportFiltersDto(
            startDate = null,
            endDate = null,
            attendeeQuery = null,
            status = EventReportFilterStatus.ALL,
        )
    }
}
