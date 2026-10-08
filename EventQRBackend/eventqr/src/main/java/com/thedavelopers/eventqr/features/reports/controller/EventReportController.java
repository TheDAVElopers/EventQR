package com.thedavelopers.eventqr.features.reports.controller;

import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportFilters;
import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportResponse;
import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportSummaryResponse;
import static com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.ReportExportRequest;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.thedavelopers.eventqr.features.reports.model.ReportExportFormat;
import com.thedavelopers.eventqr.features.reports.model.ReportFilterStatus;
import com.thedavelopers.eventqr.features.reports.model.ReportType;
import com.thedavelopers.eventqr.features.reports.service.EventReportGenerationService;
import com.thedavelopers.eventqr.features.reports.service.ReportExportService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events/{eventId}/reports")
public class EventReportController {

    private final EventReportGenerationService reportGenerationService;
    private final ReportExportService reportExportService;
    private final JwtService jwtService;

    public EventReportController(EventReportGenerationService reportGenerationService,
                                 ReportExportService reportExportService,
                                 JwtService jwtService) {
        this.reportGenerationService = reportGenerationService;
        this.reportExportService = reportExportService;
        this.jwtService = jwtService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<EventReportSummaryResponse>> summary(HttpServletRequest request,
                                                                           @PathVariable UUID eventId) {
        EventReportSummaryResponse response = reportGenerationService.summary(currentUserId(request), currentRole(request), eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{reportType}")
    public ResponseEntity<ApiResponse<EventReportResponse>> report(HttpServletRequest request,
                                                                    @PathVariable UUID eventId,
                                                                    @PathVariable ReportType reportType,
                                                                    @RequestParam(required = false)
                                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                    LocalDate startDate,
                                                                    @RequestParam(required = false)
                                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                    LocalDate endDate,
                                                                    @RequestParam(required = false) String attendeeQuery,
                                                                    @RequestParam(required = false, defaultValue = "ALL")
                                                                    ReportFilterStatus status) {
        EventReportResponse response = reportGenerationService.generate(
                currentUserId(request),
                currentRole(request),
                eventId,
                reportType,
                new EventReportFilters(startDate, endDate, attendeeQuery, status)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{reportType}/export")
    public ResponseEntity<StreamingResponseBody> export(HttpServletRequest request,
                                                       @PathVariable UUID eventId,
                                                       @PathVariable ReportType reportType,
                                                       @Valid @RequestBody(required = false) ReportExportRequest exportRequest) {
        ReportFilterStatus status = exportRequest == null || exportRequest.status() == null
                ? ReportFilterStatus.ALL
                : exportRequest.status();
        EventReportResponse report = reportGenerationService.generate(
                currentUserId(request),
                currentRole(request),
                eventId,
                reportType,
                new EventReportFilters(
                        exportRequest == null ? null : exportRequest.startDate(),
                        exportRequest == null ? null : exportRequest.endDate(),
                        exportRequest == null ? null : exportRequest.attendeeQuery(),
                        status
                )
        );

        ReportExportFormat format = parseFormat(exportRequest == null ? null : exportRequest.format());
        String fileName = reportExportService.fileName(report, format);
        String contentType = reportExportService.contentType(format);

        StreamingResponseBody body = out -> {
            try {
                if (format == ReportExportFormat.PDF) {
                    reportExportService.writePdf(report, out);
                } else {
                    reportExportService.writeCsv(report, out);
                }
                out.flush();
            } catch (Exception ex) {
                throw new RuntimeException("Unable to stream report", ex);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }

    private ReportExportFormat parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return ReportExportFormat.CSV;
        }
        return ReportExportFormat.valueOf(format.trim().toUpperCase());
    }

    private AccountRole currentRole(HttpServletRequest request) {
        return jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }
}
