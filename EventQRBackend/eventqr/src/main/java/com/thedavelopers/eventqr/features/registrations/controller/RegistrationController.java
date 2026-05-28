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
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final QrCredentialService qrCredentialService;
    private final JwtService jwtService;

    public RegistrationController(RegistrationService registrationService, QrCredentialService qrCredentialService, JwtService jwtService) {
        this.registrationService = registrationService;
        this.qrCredentialService = qrCredentialService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegistrationSubmissionResponse>> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Registration completed", registrationService.register(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RegistrationResponse>>> myRegistrations(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(registrationService.findByAttendeeUserId(userId)));
    }

    @GetMapping("/{registrationId}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> findOne(@PathVariable UUID registrationId) {
        return ResponseEntity.ok(ApiResponse.success(registrationService.findOne(registrationId)));
    }

    @DeleteMapping("/{registrationId}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> cancel(HttpServletRequest request,
                                                                    @PathVariable UUID registrationId) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Registration cancelled", registrationService.cancel(registrationId, userId)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<RegistrationResponse>>> findByEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(registrationService.findByEvent(eventId)));
    }

    @PostMapping("/{registrationId}/qr")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> generateQr(@PathVariable UUID registrationId) {
        return ResponseEntity.ok(ApiResponse.success("QR credential prepared", registrationService.getOrCreateQrCredential(registrationId)));
    }

    @PostMapping("/{registrationId}/qr/link")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> linkQr(@PathVariable UUID registrationId) {
        return ResponseEntity.ok(ApiResponse.success("QR credential linked", registrationService.linkQrCredential(registrationId)));
    }

    @GetMapping("/{registrationId}/qr/one-time")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> oneTimeQr(@PathVariable UUID registrationId) {
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success("One-time QR credential", qrCredentialService.markDisplayedOnce(qr.qrCredentialId())));
    }

    @PostMapping("/{registrationId}/qr/download")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> downloadQr(@PathVariable UUID registrationId) {
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR download registered", qrCredentialService.markDownloaded(qr.qrCredentialId())));
    }

    @PostMapping("/{registrationId}/qr/email")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> emailQr(@PathVariable UUID registrationId) {
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR email queued", qrCredentialService.markEmailQueued(qr.qrCredentialId())));
    }

    @PostMapping("/{registrationId}/qr/email/retry")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> retryEmailQr(@PathVariable UUID registrationId) {
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success("QR email retried", qrCredentialService.markEmailQueued(qr.qrCredentialId())));
    }

    @GetMapping("/{registrationId}/email-status")
    public ResponseEntity<ApiResponse<String>> emailStatus(@PathVariable UUID registrationId) {
        QrCredentialSnapshot qr = registrationService.getOrCreateQrCredential(registrationId);
        return ResponseEntity.ok(ApiResponse.success(qr.deliveryStatus().name()));
    }
}