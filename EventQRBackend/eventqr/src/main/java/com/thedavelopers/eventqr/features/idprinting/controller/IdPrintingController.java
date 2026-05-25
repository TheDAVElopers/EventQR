package com.thedavelopers.eventqr.features.idprinting.controller;

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

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.service.IdPrintingService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/id-printing")
public class IdPrintingController {

    private final IdPrintingService idPrintingService;

    public IdPrintingController(IdPrintingService idPrintingService) {
        this.idPrintingService = idPrintingService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IdPrintResponse>> print(@Valid @RequestBody IdPrintRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ID print simulated", idPrintingService.print(request)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<IdPrintResponse>>> findByEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(idPrintingService.findByEvent(eventId)));
    }
}