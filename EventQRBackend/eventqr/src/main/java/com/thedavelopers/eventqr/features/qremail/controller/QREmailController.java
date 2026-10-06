package com.thedavelopers.eventqr.features.qremail.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/qr-email")
public class QREmailController {

    private final QREmailService qrEmailService;
    private final RegistrationService registrationService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public QREmailController(QREmailService qrEmailService, RegistrationService registrationService, JwtService jwtService,
                             EventService eventService, EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.qrEmailService = qrEmailService;
        this.registrationService = registrationService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    @PostMapping("/registration/{registrationId}")
    public ResponseEntity<ApiResponse<QREmailService.DeliveryResult>> sendForRegistration(
            HttpServletRequest request, @PathVariable UUID registrationId) {
        requireAccess(request, registrationService.requireById(registrationId));
        return ResponseEntity.ok(ApiResponse.success("QR email delivery attempted",
                qrEmailService.sendForRegistration(registrationId)));
    }

    @PostMapping("/credential/{qrCredentialId}")
    public ResponseEntity<ApiResponse<QREmailService.DeliveryResult>> sendForCredential(
            HttpServletRequest request, @PathVariable UUID qrCredentialId) {
        RegistrationSnapshot registration = registrationService.findByQrCredentialId(qrCredentialId)
                .orElseThrow(() -> new com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException(
                        "Registration not found for QR credential: " + qrCredentialId));
        requireAccess(request, registration);
        return ResponseEntity.ok(ApiResponse.success("QR email delivery attempted",
                qrEmailService.sendForCredential(qrCredentialId)));
    }

    private void requireAccess(HttpServletRequest request, RegistrationSnapshot registration) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        if (registration.attendeeUserId().equals(callerId)) {
            return;
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
        throw new ForbiddenException("Access denied to QR email");
    }
}