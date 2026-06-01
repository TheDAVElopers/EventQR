package com.thedavelopers.eventqr.features.staff.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointAdjustmentRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.features.scanning.service.ScanPurposeService;
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.ScanVerificationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final TransactionService transactionService;
    private final RewardService rewardService;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;
    private final ScanPurposeService scanPurposeService;
    private final JwtService jwtService;

    public StaffController(EventService eventService,
                           RegistrationService registrationService,
                           TransactionService transactionService,
                           RewardService rewardService,
                           EventStaffAssignmentRepository eventStaffAssignmentRepository,
                           ScanPurposeService scanPurposeService,
                           JwtService jwtService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.transactionService = transactionService;
        this.rewardService = rewardService;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
        this.scanPurposeService = scanPurposeService;
        this.jwtService = jwtService;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<StaffAssignedEventResponse>>> events(HttpServletRequest request) {
        UUID staffUserId = currentUserId(request);
        List<StaffAssignedEventResponse> events = eventStaffAssignmentRepository.findByStaffUserIdAndActiveTrue(staffUserId).stream()
                .map(assignment -> {
                    EventResponse event = eventService.findOne(assignment.getEventId());
                    return new StaffAssignedEventResponse(
                            assignment.getId(),
                            event.eventId(),
                            event.title(),
                            event.description(),
                            event.location(),
                            event.eventStartAt(),
                            event.eventEndAt(),
                            event.status(),
                            assignment.isCanScan() && event.status() != EventStatus.ENDED,
                            assignment.isCanPrintId(),
                            assignment.isCanViewLogs(),
                            assignment.isCanManageRewards()
                    );
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> event(HttpServletRequest request, @PathVariable UUID eventId) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(eventService.findOne(eventId)));
    }

    @GetMapping("/events/{eventId}/scan-purposes")
    public ResponseEntity<ApiResponse<List<ScanPurposeSnapshot>>> scanPurposes(HttpServletRequest request,
                                                                              @PathVariable UUID eventId) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(scanPurposeService.listByEventId(eventId).stream().filter(ScanPurposeSnapshot::active).toList()));
    }

    @PostMapping("/events/{eventId}/scan/verify")
    public ResponseEntity<ApiResponse<ScanVerificationResponse>> verify(HttpServletRequest request,
                                                                         @PathVariable UUID eventId,
                                                                         @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.verify(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/entry")
    public ResponseEntity<ApiResponse<TransactionResponse>> entry(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Entry recorded", transactionService.record(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/attendance")
    public ResponseEntity<ApiResponse<TransactionResponse>> attendance(HttpServletRequest request,
                                                                      @PathVariable UUID eventId,
                                                                      @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Attendance recorded", transactionService.record(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/benefit-claim")
    public ResponseEntity<ApiResponse<TransactionResponse>> benefitClaim(HttpServletRequest request,
                                                                      @PathVariable UUID eventId,
                                                                      @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Benefit claim recorded", transactionService.record(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/booth-visit")
    public ResponseEntity<ApiResponse<TransactionResponse>> boothVisit(HttpServletRequest request,
                                                                       @PathVariable UUID eventId,
                                                                       @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Booth visit recorded", transactionService.record(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/reward-redemption")
    public ResponseEntity<ApiResponse<TransactionResponse>> rewardRedemptionScan(HttpServletRequest request,
                                                                               @PathVariable UUID eventId,
                                                                               @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Reward redemption scan recorded", transactionService.record(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/exit")
    public ResponseEntity<ApiResponse<TransactionResponse>> exit(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Exit recorded", transactionService.record(normalize(eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/reject")
    public ResponseEntity<ApiResponse<TransactionResponse>> reject(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Scan rejected", transactionService.record(normalize(eventId, body))));
    }

    @GetMapping("/events/{eventId}/scan/latest")
    public ResponseEntity<ApiResponse<TransactionResponse>> latest(HttpServletRequest request,
                                                                   @PathVariable UUID eventId) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.latest(eventId)));
    }

    @GetMapping("/events/{eventId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> transactions(HttpServletRequest request,
                                                                              @PathVariable UUID eventId) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.findByEvent(eventId)));
    }

    @GetMapping("/events/{eventId}/attendees/{attendeeId}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> attendee(HttpServletRequest request,
                                                                      @PathVariable UUID eventId,
                                                                      @PathVariable UUID attendeeId) {
        requireActiveAssignment(request, eventId);
        RegistrationResponse registration = registrationService.findByAttendeeUserId(attendeeId).stream()
                .filter(item -> item.eventId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException("Attendee not found for event"));
        return ResponseEntity.ok(ApiResponse.success(registration));
    }

    @PostMapping("/events/{eventId}/rewards/{rewardId}/redeem")
    public ResponseEntity<ApiResponse<RewardRedemptionResponse>> redeem(HttpServletRequest request,
                                                                        @PathVariable UUID eventId,
                                                                        @PathVariable UUID rewardId,
                                                                        @Valid @RequestBody RewardRedemptionRequest body) {
        requireActiveAssignment(request, eventId);
        RewardRedemptionRequest normalized = new RewardRedemptionRequest(eventId, body.attendeeUserId(), rewardId);
        return ResponseEntity.ok(ApiResponse.success("Reward redeemed", rewardService.redeem(normalized)));
    }

    @PostMapping("/events/{eventId}/points/assign")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> assignPoints(HttpServletRequest request,
                                                                          @PathVariable UUID eventId,
                                                                          @Valid @RequestBody PointAdjustmentRequest body) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Points assigned", rewardService.assignPoints(eventId, body.attendeeUserId(), body.points(), body.reason())));
    }

    @PostMapping("/events/{eventId}/points/deduct")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> deductPoints(HttpServletRequest request,
                                                                          @PathVariable UUID eventId,
                                                                          @Valid @RequestBody PointAdjustmentRequest body) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Points deducted", rewardService.deductPoints(eventId, body.attendeeUserId(), body.points(), body.reason())));
    }

    private TransactionRequest normalize(UUID eventId, TransactionRequest request) {
        return new TransactionRequest(eventId, request.scanPurposeId(), request.qrValue(), request.staffUserId(), request.notes());
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }

    private EventStaffAssignment requireActiveAssignment(HttpServletRequest request, UUID eventId) {
        UUID staffUserId = currentUserId(request);
        AccountRole tokenRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (tokenRole == AccountRole.ORGANIZER || tokenRole == AccountRole.ADMIN || tokenRole == AccountRole.SUPER_ADMIN) {
            return null;
        }
        return eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, staffUserId)
                .orElseThrow(() -> new ForbiddenException("Staff user is not actively assigned to this event"));
    }

    private void requireScanPermission(HttpServletRequest request, UUID eventId) {
        EventStaffAssignment assignment = requireActiveAssignment(request, eventId);
        EventResponse event = eventService.findOne(eventId);
        if (event.status() == EventStatus.ENDED) {
            throw new ForbiddenException("Event has ended. Scanning is disabled.");
        }
        if (assignment != null && !assignment.isCanScan()) {
            throw new ForbiddenException("Staff user is not allowed to scan for this event");
        }
    }
}
