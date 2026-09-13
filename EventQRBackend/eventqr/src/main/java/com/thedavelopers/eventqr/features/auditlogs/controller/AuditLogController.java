package com.thedavelopers.eventqr.features.auditlogs.controller;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogRequest;
import com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogResponse;
import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService auditLogService;
    private final JwtService jwtService;

    @GetMapping("/admin/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAdminAuditLogs(HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(auditLogService.findAll()));
    }

    @GetMapping("/admin/events/{eventId}/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getOrganizerAuditLogs(HttpServletRequest request,
                                                                                     @PathVariable UUID eventId) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(auditLogService.findByEvent(eventId)));
    }

    @PostMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Void>> createAuditLog(HttpServletRequest request, @Valid @RequestBody AuditLogRequest body) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        auditLogService.log(body, userId, "System User");
        return ResponseEntity.ok(ApiResponse.success("Audit log created", null));
    }

    private void requireAdmin(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role != AccountRole.ADMIN && role != AccountRole.SUPER_ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Admin access required");
        }
    }
}