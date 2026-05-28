package com.thedavelopers.eventqr.features.qrcredentials.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/qr-credentials")
public class QrCredentialController {

    private final QrCredentialService qrCredentialService;
    private final RegistrationService registrationService;
    private final JwtService jwtService;

    public QrCredentialController(QrCredentialService qrCredentialService,
                                  RegistrationService registrationService,
                                  JwtService jwtService) {
        this.qrCredentialService = qrCredentialService;
        this.registrationService = registrationService;
        this.jwtService = jwtService;
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

    private QrCredentialSnapshot loadByRegistrationId(UUID registrationId) {
        return qrCredentialService.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found for registration " + registrationId));
    }

    private QrCredentialSnapshot loadById(UUID qrCredentialId) {
        return qrCredentialService.findById(qrCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found: " + qrCredentialId));
    }

    private void requireAccessibleRegistration(HttpServletRequest request, UUID registrationId) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ATTENDEE) {
            UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
            registrationService.findOneForAttendee(registrationId, userId);
        }
    }
}