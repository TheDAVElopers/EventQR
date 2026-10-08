package com.thedavelopers.eventqr.features.reports.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.thedavelopers.eventqr.features.reports.model.ReportEmptyState;
import com.thedavelopers.eventqr.features.reports.model.ReportFilterStatus;
import com.thedavelopers.eventqr.features.reports.model.ReportType;

public final class EventReportDtos {

    private EventReportDtos() {
    }

    public record EventReportSummaryResponse(UUID eventId, String eventName, long registeredCount, long checkedInCount,
                                             long exitedCount, boolean hasAnyRecords) {
    }

    public record EventReportFilters(LocalDate startDate, LocalDate endDate, String attendeeQuery,
                                     ReportFilterStatus status) {
    }

    public record EventReportRow(List<String> values) {
    }

    /**
     * {@code total} is the sum of {@code chartSeries} (full, unbucketed). For the POINTS report this is the
     * total points AWARDED (positive rows only), so it differs from the signed sum of the table rows,
     * which also include deductions.
     */
    public record EventReportResponse(UUID eventId, ReportType reportType, String reportTitle, String eventName,
                                      Instant generatedAt, List<String> columns, List<EventReportRow> rows,
                                      Map<String, Long> chartSeries, long total, ReportEmptyState emptyState,
                                      EventReportFilters appliedFilters) {
    }

    public record ReportExportRequest(String format, LocalDate startDate, LocalDate endDate, String attendeeQuery,
                                      ReportFilterStatus status) {
    }
}
