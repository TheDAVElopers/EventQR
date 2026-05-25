package com.thedavelopers.eventqr.features.scanpurposes.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest;
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse;
import com.thedavelopers.eventqr.features.scanpurposes.service.ScanPurposeService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/scan-purposes")
public class ScanPurposeController {

    private final ScanPurposeService scanPurposeService;

    public ScanPurposeController(ScanPurposeService scanPurposeService) {
        this.scanPurposeService = scanPurposeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScanPurposeResponse>> create(@Valid @RequestBody ScanPurposeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Scan purpose created", scanPurposeService.create(request)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<ScanPurposeResponse>>> findByEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(scanPurposeService.findByEventId(eventId)));
    }
}