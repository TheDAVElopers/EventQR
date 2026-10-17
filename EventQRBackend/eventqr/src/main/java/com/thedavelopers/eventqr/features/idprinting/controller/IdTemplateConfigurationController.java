package com.thedavelopers.eventqr.features.idprinting.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateConfigDto;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateConfigResponse;
import com.thedavelopers.eventqr.features.idprinting.service.IdTemplateConfigurationService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * SDD Module 3.7 — Configure ID Display Fields (organizer-facing).
 *
 * Route note: the SDD specifies PUT/GET /api/events/{eventId}/id-template; this codebase
 * serves all mobile-facing routes under /api/v1 (see EventReportController), so the version
 * prefix is added for consistency.
 *
 * Access: PUT is the event's organizer only (enforced in the service). GET is the organizer, admins, or staff
 * actively assigned to the event, because the staff print screens read it to render the card; when the event has
 * no template yet, GET creates and returns the default one (QR code + attendee name only).
 *
 * No logo upload, color editing, or template style selection exists here per SDD 3.7 scope
 * (see IdTemplateConfigurationService deviation note).
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/id-template")
public class IdTemplateConfigurationController {

    private final IdTemplateConfigurationService configurationService;
    private final JwtService jwtService;
    private final IdPrintingAccess access;

    public IdTemplateConfigurationController(IdTemplateConfigurationService configurationService,
                                             JwtService jwtService, IdPrintingAccess access) {
        this.configurationService = configurationService;
        this.jwtService = jwtService;
        this.access = access;
    }

    @PutMapping
    public ResponseEntity<ApiResponse<IdTemplateConfigResponse>> save(HttpServletRequest request,
                                                                      @PathVariable UUID eventId,
                                                                      @Valid @RequestBody IdTemplateConfigDto body) {
        // Path param is authoritative; body.eventId is intentionally ignored.
        IdTemplateConfigResponse saved = configurationService.saveConfig(
                eventId, body.visibleFields(), currentUserId(request));
        return ResponseEntity.ok(ApiResponse.success("ID display settings saved", saved));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<IdTemplateConfigResponse>> get(HttpServletRequest request,
                                                                     @PathVariable UUID eventId) {
        access.requireStaffOrOwner(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(configurationService.getConfig(eventId)));
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }
}
