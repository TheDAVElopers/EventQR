package com.thedavelopers.eventqr.features.qrcredentials.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/qr-credentials")
public class QrCredentialController {

    private final QrCredentialService qrCredentialService;
    private final RegistrationService registrationService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public QrCredentialController(QrCredentialService qrCredentialService,
                                  RegistrationService registrationService,
                                  JwtService jwtService,
                                  EventService eventService,
                                  EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.qrCredentialService = qrCredentialService;
        this.registrationService = registrationService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    @GetMapping("/registration/{registrationId}")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> findByRegistration(HttpServletRequest request,
                                                                                @PathVariable UUID registrationId) {
        requireAccessibleRegistration(request, registrationId);
        return ResponseEntity.ok(ApiResponse.success(loadByRegistrationId(registrationId)));
    }

    @GetMapping("/{qrCredentialId}")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> findById(HttpServletRequest request,
                                                                      @PathVariable UUID qrCredentialId) {
        QrCredentialSnapshot qrCredential = loadById(qrCredentialId);
        requireAccessibleRegistration(request, qrCredential.registrationId());
        return ResponseEntity.ok(ApiResponse.success(qrCredential));
    }

    @PatchMapping("/{qrCredentialId}/displayed")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> markDisplayed(HttpServletRequest request,
                                                                            @PathVariable UUID qrCredentialId) {
        requireAccessibleRegistration(request, loadById(qrCredentialId).registrationId());
        return ResponseEntity.ok(ApiResponse.success("QR display updated", qrCredentialService.markDisplayedOnce(qrCredentialId)));
    }

    @PatchMapping("/{qrCredentialId}/downloaded")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> markDownloaded(HttpServletRequest request,
                                                                              @PathVariable UUID qrCredentialId) {
        requireAccessibleRegistration(request, loadById(qrCredentialId).registrationId());
        return ResponseEntity.ok(ApiResponse.success("QR download updated", qrCredentialService.markDownloaded(qrCredentialId)));
    }

    @GetMapping("/attendees/me/registration/{registrationId}")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> findMyByRegistration(HttpServletRequest request,
                                                                                  @PathVariable UUID registrationId) {
        requireOwnRegistration(request, registrationId);
        return ResponseEntity.ok(ApiResponse.success(loadByRegistrationId(registrationId)));
    }

    @GetMapping("/attendees/me/{qrCredentialId}")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> findMyById(HttpServletRequest request,
                                                                        @PathVariable UUID qrCredentialId) {
        QrCredentialSnapshot qrCredential = loadById(qrCredentialId);
        requireOwnRegistration(request, qrCredential.registrationId());
        return ResponseEntity.ok(ApiResponse.success(qrCredential));
    }

    @PatchMapping("/attendees/me/{qrCredentialId}/displayed")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> markMyDisplayed(HttpServletRequest request,
                                                                              @PathVariable UUID qrCredentialId) {
        requireOwnRegistration(request, loadById(qrCredentialId).registrationId());
        return ResponseEntity.ok(ApiResponse.success("QR display updated", qrCredentialService.markDisplayedOnce(qrCredentialId)));
    }

    @PatchMapping("/attendees/me/{qrCredentialId}/downloaded")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> markMyDownloaded(HttpServletRequest request,
                                                                                @PathVariable UUID qrCredentialId) {
        requireOwnRegistration(request, loadById(qrCredentialId).registrationId());
        return ResponseEntity.ok(ApiResponse.success("QR download updated", qrCredentialService.markDownloaded(qrCredentialId)));
    }

    private QrCredentialSnapshot loadByRegistrationId(UUID registrationId) {
        return qrCredentialService.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found for registration " + registrationId));
    }

    private QrCredentialSnapshot loadById(UUID qrCredentialId) {
        return qrCredentialService.findById(qrCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found: " + qrCredentialId));
    }

    private void requireAccessibleRegistration(HttpServletRequest request, UUID registrationId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse registration = registrationService.findOne(registrationId);
        if (role == AccountRole.ATTENDEE) {
            if (registration.attendeeUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("You can only access your own QR credential");
        }
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(registration.eventId()).organizerUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(registration.eventId(), callerId)) {
                return;
            }
            throw new ForbiddenException("Staff user is not actively assigned to this event");
        }
        throw new ForbiddenException("Access denied to QR credential");
    }

    private void requireOwnRegistration(HttpServletRequest request, UUID registrationId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse registration = registrationService.findOne(registrationId);
        if (!registration.attendeeUserId().equals(callerId)) {
            throw new ForbiddenException("You can only access your own QR credential");
        }
    }
}