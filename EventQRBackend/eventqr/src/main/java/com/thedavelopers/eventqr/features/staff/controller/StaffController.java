package com.thedavelopers.eventqr.features.staff.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import com.thedavelopers.eventqr.features.transactions.model.dto.StaffTodaySummary;
import com.thedavelopers.eventqr.features.transactions.model.dto.StaffTransactionSummary;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountRoles;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
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

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> myTransactions(HttpServletRequest request,
                                                                                 @RequestParam(required = false) UUID eventId,
                                                                                 @RequestParam(required = false) UUID purposeId,
                                                                                 @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "20") int size) {
        UUID staffUserId = currentUserId(request);
        if (eventId != null) {
            requireActiveAssignment(request, eventId);
        }
        return ResponseEntity.ok(ApiResponse.success(transactionService.findForStaff(staffUserId, eventId, purposeId, pageable(page, size))));
    }

    @GetMapping("/transactions/summary")
    public ResponseEntity<ApiResponse<StaffTransactionSummary>> myTransactionSummary(HttpServletRequest request,
                                                                                     @RequestParam(required = false) UUID eventId,
                                                                                     @RequestParam(required = false) UUID purposeId) {
        UUID staffUserId = currentUserId(request);
        if (eventId != null) {
            requireActiveAssignment(request, eventId);
        }
        return ResponseEntity.ok(ApiResponse.success(transactionService.summarizeForStaff(staffUserId, eventId, purposeId)));
    }

    @GetMapping("/transactions/today/summary")
    public ResponseEntity<ApiResponse<StaffTodaySummary>> myTodaySummary(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.summarizeTodayForStaff(currentUserId(request))));
    }

    @GetMapping("/transactions/today")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> myTransactionsToday(HttpServletRequest request) {
        UUID staffUserId = currentUserId(request);
        return ResponseEntity.ok(ApiResponse.success(transactionService.findForStaffToday(staffUserId)));
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
        return ResponseEntity.ok(ApiResponse.success(transactionService.verify(normalize(request, eventId, body))));
    }

    @PostMapping("/events/{eventId}/scan/entry")
    public ResponseEntity<ApiResponse<TransactionResponse>> entry(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return recordScan(request, eventId, body, "Entry", ScanPurposeCode.ENTRY);
    }

    @PostMapping("/events/{eventId}/scan/attendance")
    public ResponseEntity<ApiResponse<TransactionResponse>> attendance(HttpServletRequest request,
                                                                      @PathVariable UUID eventId,
                                                                      @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return recordScan(request, eventId, body, "Attendance", ScanPurposeCode.ATTENDANCE);
    }

    @PostMapping("/events/{eventId}/scan/benefit-claim")
    public ResponseEntity<ApiResponse<TransactionResponse>> benefitClaim(HttpServletRequest request,
                                                                      @PathVariable UUID eventId,
                                                                      @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return recordScan(request, eventId, body, "Benefit claim", ScanPurposeCode.BENEFIT_CLAIM);
    }

    @PostMapping("/events/{eventId}/scan/booth-visit")
    public ResponseEntity<ApiResponse<TransactionResponse>> boothVisit(HttpServletRequest request,
                                                                       @PathVariable UUID eventId,
                                                                       @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return recordScan(request, eventId, body, "Booth visit", ScanPurposeCode.BOOTH_VISIT, ScanPurposeCode.SESSION_VISIT);
    }

    @PostMapping("/events/{eventId}/scan/reward-redemption")
    public ResponseEntity<ApiResponse<TransactionResponse>> rewardRedemptionScan(HttpServletRequest request,
                                                                               @PathVariable UUID eventId,
                                                                               @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return recordScan(request, eventId, body, "Reward redemption scan", ScanPurposeCode.REWARD_REDEMPTION_SCAN, ScanPurposeCode.REWARD_REDEMPTION);
    }

    @PostMapping("/events/{eventId}/scan/exit")
    public ResponseEntity<ApiResponse<TransactionResponse>> exit(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return recordScan(request, eventId, body, "Exit", ScanPurposeCode.EXIT);
    }

    @PostMapping("/events/{eventId}/scan/reject")
    public ResponseEntity<ApiResponse<TransactionResponse>> reject(HttpServletRequest request,
                                                                  @PathVariable UUID eventId,
                                                                  @Valid @RequestBody TransactionRequest body) {
        requireScanPermission(request, eventId);
        return ResponseEntity.ok(ApiResponse.success("Scan rejected", transactionService.rejectManually(normalize(request, eventId, body))));
    }

    @GetMapping("/events/{eventId}/scan/latest")
    public ResponseEntity<ApiResponse<TransactionResponse>> latest(HttpServletRequest request,
                                                                   @PathVariable UUID eventId) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.latest(eventId)));
    }

    @GetMapping("/events/{eventId}/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> transactions(HttpServletRequest request,
                                                                                @PathVariable UUID eventId,
                                                                                @RequestParam(required = false) UUID attendeeUserId,
                                                                                @RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "20") int size) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.findForEventStaff(eventId, attendeeUserId, pageable(page, size))));
    }

    @GetMapping("/events/{eventId}/transactions/today")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> transactionsToday(HttpServletRequest request,
                                                                                   @PathVariable UUID eventId) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.findByEventToday(eventId)));
    }

    @GetMapping("/events/{eventId}/attendees/{attendeeId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> attendeeTransactions(HttpServletRequest request,
                                                                                        @PathVariable UUID eventId,
                                                                                        @PathVariable UUID attendeeId) {
        requireActiveAssignment(request, eventId);
        return ResponseEntity.ok(ApiResponse.success(transactionService.findRecentByEventAndAttendee(eventId, attendeeId, 5)));
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
        if (!eventService.findOne(eventId).rewardsEnabled()) {
            throw new BadRequestException(TransactionService.REWARDS_DISABLED_MESSAGE);
        }
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

    /**
     * Records a scan on a purpose-specific route. The scan purpose in the body must be one this route handles,
     * and the message reflects the real outcome (an approved scan is "recorded", a rejected one carries its reason).
     */
    private ResponseEntity<ApiResponse<TransactionResponse>> recordScan(HttpServletRequest request, UUID eventId, TransactionRequest body, String label,
                                                                       ScanPurposeCode... allowed) {
        var purpose = scanPurposeService.requireActive(body.scanPurposeId());
        if (!eventId.equals(purpose.eventId())) {
            throw new ForbiddenException("Scan purpose does not belong to the event");
        }
        ScanPurposeCode actual = purpose.code();
        if (!java.util.Arrays.asList(allowed).contains(actual)) {
            throw new BadRequestException("Scan purpose " + actual + " cannot be recorded on the " + label.toLowerCase() + " route");
        }
        TransactionResponse response = transactionService.record(normalize(request, eventId, body));
        return ResponseEntity.ok(ApiResponse.success(TransactionService.describeOutcome(label, response), response));
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private TransactionRequest normalize(HttpServletRequest httpRequest, UUID eventId, TransactionRequest request) {
        // staffUserId is always the authenticated caller; any body value is ignored.
        return new TransactionRequest(eventId, request.scanPurposeId(), request.qrValue(), request.shortId(), currentUserId(httpRequest), request.notes(),
                request.clientRequestId());
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }

    private EventStaffAssignment requireActiveAssignment(HttpServletRequest request, UUID eventId) {
        UUID staffUserId = currentUserId(request);
        AccountRole tokenRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (AccountRoles.isAtLeast(tokenRole, AccountRole.ADMIN)) {
            return null;
        }
        if (tokenRole == AccountRole.ORGANIZER) {
            if (eventService.findOne(eventId).organizerUserId().equals(staffUserId)) {
                return null;
            }
            return eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, staffUserId)
                    .orElseThrow(() -> new ForbiddenException("Organizer is not assigned to this event"));
        }
        if (tokenRole == AccountRole.ATTENDEE) {
            throw new ForbiddenException("Attendee role is not permitted for staff operations");
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
