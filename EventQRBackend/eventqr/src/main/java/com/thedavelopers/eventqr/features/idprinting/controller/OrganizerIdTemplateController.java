package com.thedavelopers.eventqr.features.idprinting.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.thedavelopers.eventqr.features.idprinting.controller.IdPrintingAccess.Caller;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdBatchPrintRequest;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintConfirmRequest;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateRequest;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.service.IdPrintingService;
import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse;
import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1")
public class OrganizerIdTemplateController {

    private final IdPrintingService idPrintingService;
    private final FileStorageService fileStorageService;
    private final IdPrintingAccess access;

    public OrganizerIdTemplateController(IdPrintingService idPrintingService, FileStorageService fileStorageService,
                                         IdPrintingAccess access) {
        this.idPrintingService = idPrintingService;
        this.fileStorageService = fileStorageService;
        this.access = access;
    }

    @GetMapping("/organizer/events/{eventId}/id-templates")
    public ResponseEntity<ApiResponse<List<IdTemplate>>> list(HttpServletRequest request, @PathVariable UUID eventId) {
        access.requireOrganizerOrAdmin(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(idPrintingService.listTemplates(eventId)));
    }

    @PostMapping("/organizer/events/{eventId}/id-template")
    public ResponseEntity<ApiResponse<IdTemplate>> create(HttpServletRequest request,
                                                          @PathVariable UUID eventId,
                                                          @Valid @RequestBody IdTemplateRequest body) {
        access.requireOrganizerOrAdmin(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("ID template saved", idPrintingService.saveTemplate(eventId, body)));
    }

    /** Returns the active template; like GET /events/{id}/id-template it creates the default one if none exists. */
    @GetMapping("/organizer/events/{eventId}/id-template")
    public ResponseEntity<ApiResponse<IdTemplate>> get(HttpServletRequest request, @PathVariable UUID eventId) {
        access.requireOrganizerOrAdmin(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(idPrintingService.getTemplate(eventId)));
    }

    @PatchMapping("/organizer/events/{eventId}/id-template")
    public ResponseEntity<ApiResponse<IdTemplate>> update(HttpServletRequest request,
                                                          @PathVariable UUID eventId,
                                                          @Valid @RequestBody IdTemplateRequest body) {
        access.requireOrganizerOrAdmin(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("ID template updated", idPrintingService.saveTemplate(eventId, body)));
    }

    @GetMapping("/organizer/events/{eventId}/id-template/preview")
    public ResponseEntity<ApiResponse<IdTemplate>> preview(HttpServletRequest request, @PathVariable UUID eventId) {
        access.requireOrganizerOrAdmin(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(idPrintingService.getTemplate(eventId)));
    }

    @PostMapping("/organizer/events/{eventId}/id-template/logo")
    public ResponseEntity<ApiResponse<StoredFileResponse>> uploadLogo(HttpServletRequest request,
                                                                       @PathVariable UUID eventId,
                                                                       @RequestParam("file") MultipartFile file) {
        access.requireOrganizerOrAdmin(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Logo stored", fileStorageService.store(eventId, "id-template-logo", file)));
    }

    /**
     * Every print log of the event, including PENDING, CANCELLED and FAILED rows, each with its effective
     * status. An attendee has a printed ID only if one of their rows has status PRINTED.
     */
    @GetMapping("/staff/events/{eventId}/print-logs")
    public ResponseEntity<ApiResponse<List<IdPrintResponse>>> printLogs(HttpServletRequest request,
                                                                         @PathVariable UUID eventId) {
        access.requireStaffOrOwner(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(idPrintingService.findByEvent(eventId)));
    }

    /**
     * Confirms the outcome of a PENDING print: PRINTED, CANCELLED or FAILED. Allowed for the staff member who
     * started the print, the event organizer and admins. 404 if the log is not in this event; 409 if not PENDING.
     */
    @PatchMapping("/staff/events/{eventId}/print-logs/{logId}")
    public ResponseEntity<ApiResponse<IdPrintResponse>> confirmPrint(HttpServletRequest request,
                                                                     @PathVariable UUID eventId,
                                                                     @PathVariable UUID logId,
                                                                     @Valid @RequestBody IdPrintConfirmRequest body) {
        Caller caller = access.requireStaffOrOwner(request, eventId);
        IdPrintResponse confirmed = idPrintingService.confirm(eventId, logId, body.outcome(), caller.userId(),
                caller.eventManager());
        return ResponseEntity.ok(ApiResponse.success("Print outcome recorded", confirmed));
    }

    @PostMapping("/staff/events/{eventId}/attendees/{attendeeId}/id-preview")
    public ResponseEntity<ApiResponse<IdPrintResponse>> previewForAttendee(HttpServletRequest request,
                                                                            @PathVariable UUID eventId,
                                                                            @PathVariable UUID attendeeId) {
        Caller caller = access.requireStaffOrOwner(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(
                idPrintingService.previewForAttendee(eventId, attendeeId, caller.userId())));
    }

    @PostMapping("/staff/events/{eventId}/attendees/{attendeeId}/print-id")
    public ResponseEntity<ApiResponse<IdPrintResponse>> print(HttpServletRequest request,
                                                              @PathVariable UUID eventId,
                                                              @PathVariable UUID attendeeId) {
        Caller caller = access.requireStaffOrOwner(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(
                idPrintingService.printForAttendee(eventId, attendeeId, caller.userId())));
    }

    /** Kept for older clients; identical to print-id now that the reprint flag is derived from PRINTED history. */
    @PostMapping("/staff/events/{eventId}/attendees/{attendeeId}/reprint-id")
    public ResponseEntity<ApiResponse<IdPrintResponse>> reprint(HttpServletRequest request,
                                                                @PathVariable UUID eventId,
                                                                @PathVariable UUID attendeeId) {
        Caller caller = access.requireStaffOrOwner(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(
                idPrintingService.printForAttendee(eventId, attendeeId, caller.userId())));
    }

    @PostMapping("/staff/events/{eventId}/print-id-batch")
    public ResponseEntity<ApiResponse<List<IdPrintResponse>>> printBatch(HttpServletRequest request,
                                                                         @PathVariable UUID eventId,
                                                                         @Valid @RequestBody IdBatchPrintRequest body) {
        Caller caller = access.requireStaffOrOwner(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(
                idPrintingService.printBatch(eventId, body.attendeeUserIds(), caller.userId())));
    }
}
