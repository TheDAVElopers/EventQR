package com.thedavelopers.eventqr.features.organizer.reports

import android.content.Context
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.safeApiCall
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportExportRequestDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportedReportFile(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

class OrganizerReportsRepository(private val context: Context) {
    private val apiService = ApiClient.getService(context)

    suspend fun fetchSummary(eventId: String): NetworkResult<EventReportSummaryDto> =
        safeApiCall { apiService.getEventReportSummary(eventId) }

    suspend fun generateReport(
        eventId: String,
        reportType: EventReportType,
        filters: EventReportFiltersDto,
        page: Int = 0,
        size: Int = 20,
    ): NetworkResult<EventReportDto> = safeApiCall {
        apiService.getEventReportByType(
            eventId = eventId,
            reportType = reportType,
            startDate = filters.startDate?.toString(),
            endDate = filters.endDate?.toString(),
            attendeeQuery = filters.attendeeQuery?.trim()?.takeIf { it.isNotBlank() },
            status = filters.status,
            page = page,
            size = size,
        )
    }

    suspend fun exportReport(
        eventId: String,
        reportType: EventReportType,
        format: String,
        filters: EventReportFiltersDto,
    ): NetworkResult<ExportedReportFile> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.exportEventReport(
                eventId = eventId,
                reportType = reportType,
                request = EventReportExportRequestDto(
                    format = format,
                    startDate = filters.startDate,
                    endDate = filters.endDate,
                    attendeeQuery = filters.attendeeQuery?.trim()?.takeIf { it.isNotBlank() },
                    status = filters.status,
                ),
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                throw IllegalStateException(errorBody.ifBlank { context.getString(R.string.organizer_reports_export_unable) })
            }
            val body = response.body() ?: throw IllegalStateException(context.getString(R.string.organizer_reports_export_empty))
            val fileName = response.headers()["Content-Disposition"]
                ?.substringAfter("filename=")
                ?.trim()
                ?.trim('"')
                ?.ifBlank { null }
                ?: "event-report.${format.lowercase()}"
            val contentType = response.headers()["Content-Type"].orEmpty().ifBlank {
                if (format.equals("PDF", ignoreCase = true)) "application/pdf" else "text/csv"
            }
            ExportedReportFile(fileName = fileName, contentType = contentType, bytes = body.bytes())
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { throwable -> NetworkResult.Error(throwable.message ?: context.getString(R.string.organizer_reports_export_unable), throwable) },
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
