package com.thedavelopers.eventqr.features.registrations.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.TooManyRequestsException;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.RegistrationRateLimiter;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final QrCredentialService qrCredentialService;
    private final QREmailService qrEmailService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;
    private final RegistrationRateLimiter registrationRateLimiter;

    public RegistrationController(RegistrationService registrationService, QrCredentialService qrCredentialService,
                                  QREmailService qrEmailService, JwtService jwtService,
                                  EventService eventService, EventStaffAssignmentRepository eventStaffAssignmentRepository,
                                  RegistrationRateLimiter registrationRateLimiter) {
        this.registrationService = registrationService;
        this.qrCredentialService = qrCredentialService;
        this.qrEmailService = qrEmailService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
        this.registrationRateLimiter = registrationRateLimiter;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegistrationSubmissionResponse>> register(HttpServletRequest request,
                                                                                @Valid @RequestBody RegistrationRequest regRequest) {
        if (!registrationRateLimiter.allow(request, regRequest.email())) {
            throw new TooManyRequestsException(
                    "Too many registration requests. Please try again later.");
        }
        return ResponseEntity.ok(ApiResponse.success("Registration completed", registrationService.register(regRequest)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<RegistrationResponse>>> myRegistrations(HttpServletRequest request,
                                                                                    @RequestParam(defaultValue = "0") int page,
                                                                                    @RequestParam(defaultValue = "20") int size) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(registrationService.findByAttendeeUserId(userId, PageRequest.of(page, size))));
    }

    @GetMapping("/{registrationId}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> findOne(HttpServletRequest request,
                                                                     @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        return ResponseEntity.ok(ApiResponse.success(registrationService.findOne(registrationId)));
    }

    @DeleteMapping("/{registrationId}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> cancel(HttpServletRequest request,
                                                                    @PathVariable UUID registrationId) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Registration cancelled", registrationService.cancel(registrationId, userId)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<Page<RegistrationResponse>>> findByEvent(HttpServletRequest request,
                                                                               @PathVariable UUID eventId,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size) {
        requireEventAccess(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(registrationService.findByEvent(eventId, PageRequest.of(page, size))));
    }

    @PostMapping("/{registrationId}/qr")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> generateQr(HttpServletRequest request,
                                                                        @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR credential prepared", registrationService.getOrCreateQrCredential(registrationId)));
    }

    @PostMapping("/{registrationId}/qr/link")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> linkQr(HttpServletRequest request,
                                                                    @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR credential linked", registrationService.linkQrCredential(registrationId)));
    }

    @GetMapping("/{registrationId}/qr/one-time")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> oneTimeQr(HttpServletRequest request,
                                                                       @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success("One-time QR credential", qrCredentialService.markDisplayedOnce(qr.qrCredentialId())));
    }

    @PostMapping("/{registrationId}/qr/download")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> downloadQr(HttpServletRequest request,
                                                                        @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR download registered", qrCredentialService.markDownloaded(qr.qrCredentialId())));
    }

    @PostMapping("/{registrationId}/qr/email")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> emailQr(HttpServletRequest request,
                                                                     @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        registrationService.getOrCreateQrCredential(registrationId);
        qrEmailService.sendForRegistrationSafelyAsync(registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR email delivery attempted",
                registrationService.getOrCreateQrCredential(registrationId)));
    }

    @PostMapping("/{registrationId}/qr/email/retry")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> retryEmailQr(HttpServletRequest request,
                                                                          @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        registrationService.getOrCreateQrCredential(registrationId);
        qrEmailService.sendForRegistrationSafelyAsync(registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR email retry attempted",
            registrationService.getOrCreateQrCredential(registrationId)));
    }

    @GetMapping("/{registrationId}/email-status")
    public ResponseEntity<ApiResponse<String>> emailStatus(HttpServletRequest request,
                                                           @PathVariable UUID registrationId) {
        requireRegistrationAccess(request, registrationId);
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success(qr.deliveryStatus().name()));
    }

    private void requireRegistrationAccess(HttpServletRequest request, UUID registrationId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        RegistrationResponse registration = registrationService.findOne(registrationId);
        if (registration.attendeeUserId().equals(callerId)) {
            return;
        }
        if (role == AccountRole.ATTENDEE) {
            if (registration.attendeeUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("You can only access your own registration");
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
        throw new ForbiddenException("Access denied to registration");
    }

    private void requireEventAccess(HttpServletRequest request, UUID eventId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                return;
            }
            throw new ForbiddenException("Event ownership required");
        }
        if (role == AccountRole.STAFF) {
            if (eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)) {
                return;
            }
            throw new ForbiddenException("Staff user is not actively assigned to this event");
        }
        throw new ForbiddenException("Access denied to event registrations");
    }
}
