package com.thedavelopers.eventqr.features.transactions.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final EventService eventService;
    private final JwtService jwtService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

    public TransactionController(TransactionService transactionService, EventService eventService,
                                 JwtService jwtService, EventStaffAssignmentRepository eventStaffAssignmentRepository) {
        this.transactionService = transactionService;
        this.eventService = eventService;
        this.jwtService = jwtService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> record(HttpServletRequest request,
                                                                   @Valid @RequestBody TransactionRequest body) {
        requireStaffOrOrganizerForEvent(request, body.eventId());
        return ResponseEntity.ok(ApiResponse.success("Transaction recorded", transactionService.record(body)));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> findByEvent(HttpServletRequest request,
                                                                              @PathVariable UUID eventId,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size) {
        requireAdminOrEventOwner(request, eventId);
        Page<TransactionResponse> result = transactionService.findByEvent(eventId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    private void requireStaffOrOrganizerForEvent(HttpServletRequest request, UUID eventId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.STAFF) {
            if (!eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)) {
                throw new ForbiddenException("Staff user is not actively assigned to this event");
            }
        } else if (role == AccountRole.ORGANIZER) {
            if (!eventService.findOne(eventId).organizerUserId().equals(callerId)) {
                throw new ForbiddenException("Event ownership required");
            }
        } else {
            throw new ForbiddenException("STAFF or ORGANIZER access required");
        }
    }

    private void requireAdminOrEventOwner(HttpServletRequest request, UUID eventId) {
        UUID callerId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            return;
        }
        if (role == AccountRole.ORGANIZER && eventService.findOne(eventId).organizerUserId().equals(callerId)) {
            return;
        }
        throw new ForbiddenException("Admin or event owner access required");
    }
}