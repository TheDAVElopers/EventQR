package com.thedavelopers.eventqr.features.qrcredentials.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/qr-credentials")
public class QrCredentialController {

    private final QrCredentialService qrCredentialService;

    public QrCredentialController(QrCredentialService qrCredentialService) {
        this.qrCredentialService = qrCredentialService;
    }

    @GetMapping("/registration/{registrationId}")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> findByRegistration(@PathVariable UUID registrationId) {
        return ResponseEntity.ok(ApiResponse.success(qrCredentialService.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found for registration " + registrationId))));
    }

    @PatchMapping("/{qrCredentialId}/displayed")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> markDisplayed(@PathVariable UUID qrCredentialId) {
        return ResponseEntity.ok(ApiResponse.success("QR display updated", qrCredentialService.markDisplayedOnce(qrCredentialId)));
    }

    @PatchMapping("/{qrCredentialId}/downloaded")
    public ResponseEntity<ApiResponse<QrCredentialSnapshot>> markDownloaded(@PathVariable UUID qrCredentialId) {
        return ResponseEntity.ok(ApiResponse.success("QR download updated", qrCredentialService.markDownloaded(qrCredentialId)));
    }
}