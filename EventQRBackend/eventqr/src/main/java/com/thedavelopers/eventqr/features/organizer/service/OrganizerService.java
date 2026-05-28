package com.thedavelopers.eventqr.features.organizer.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.*;
import com.thedavelopers.eventqr.features.organizer.model.dto.RewardSettingsRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanpurposes.model.entity.ScanPurpose;
import com.thedavelopers.eventqr.features.scanpurposes.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exception.BadRequestException;
import com.thedavelopers.eventqr.shared.exception.ConflictException;
import com.thedavelopers.eventqr.shared.exception.ForbiddenException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

@Service
@Transactional
public class OrganizerService {

    private static final List<String> DEFAULT_PERMISSIONS = List.of("Scan QR", "View attendee details");

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ScanPurposeRepository scanPurposeRepository;
    private final TransactionRuleRepository transactionRuleRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final EventStaffAssignmentRepository staffAssignmentRepository;
    private final UserProfileRepository userProfileRepository;

    public OrganizerService(EventRepository eventRepository,
                            EventRegistrationRepository registrationRepository,
                            TransactionLogRepository transactionLogRepository,
                            ScanPurposeRepository scanPurposeRepository,
                            TransactionRuleRepository transactionRuleRepository,
                            RewardRedemptionRepository rewardRedemptionRepository,
                            PointTransactionRepository pointTransactionRepository,
                            EventStaffAssignmentRepository staffAssignmentRepository,
                            UserProfileRepository userProfileRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.scanPurposeRepository = scanPurposeRepository;
        this.transactionRuleRepository = transactionRuleRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizerEventResponse> listEvents(UUID organizerUserId) {
        return eventRepository.findByOrganizerUserId(organizerUserId).stream()
                .filter(event -> event.getStatus() == EventStatus.APPROVED || event.getStatus() == EventStatus.ACTIVE
                        || event.getStatus() == EventStatus.ENDED || event.getStatus() == EventStatus.PENDING_REVIEW
                        || event.getStatus() == EventStatus.REJECTED)
                .map(this::toOrganizerEvent)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizerEventResponse event(UUID organizerUserId, UUID eventId) {
        return toOrganizerEvent(requireOrganizerEvent(organizerUserId, eventId));
    }

    public EventResponse updateEvent(UUID organizerUserId, UUID eventId, EventRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setRegistrationOpenAt(request.registrationOpenAt());
        event.setRegistrationCloseAt(request.registrationCloseAt());
        event.setEventStartAt(request.eventStartAt());
        event.setEventEndAt(request.eventEndAt());
        event.setCapacity(request.capacity());
        event.setRewardsEnabled(Boolean.TRUE.equals(request.rewardsEnabled()));
        event.setOrganizerUserId(request.organizerUserId());
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    public EventResponse updateStatus(UUID organizerUserId, UUID eventId, EventStatus status) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        event.setStatus(status);
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    public EventResponse updateRewardSettings(UUID organizerUserId, UUID eventId, RewardSettingsRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        event.setRewardsEnabled(request.enabled());
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    @Transactional(readOnly = true)
    public OrganizerDashboardResponse dashboard(UUID organizerUserId, UUID eventId) {
        return new OrganizerDashboardResponse(toOrganizerEvent(requireOrganizerEvent(organizerUserId, eventId)));
    }

    @Transactional(readOnly = true)
    public List<OrganizerAttendeeResponse> attendees(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        List<TransactionLog> logs = transactionLogRepository.findByEventId(eventId);
        return registrationRepository.findByEventId(eventId).stream()
                .map(registration -> toAttendee(registration, logs))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizerAttendeeResponse> searchAttendees(UUID organizerUserId, UUID eventId, String query) {
        String safeQuery = query == null ? "" : query.trim().toLowerCase();
        if (safeQuery.isBlank()) {
            return attendees(organizerUserId, eventId);
        }
        return attendees(organizerUserId, eventId).stream()
                .filter(attendee -> attendee.name().toLowerCase().contains(safeQuery)
                        || attendee.email().toLowerCase().contains(safeQuery)
                        || attendee.registrationStatus().toLowerCase().contains(safeQuery)
                        || attendee.currentEventStatus().toLowerCase().contains(safeQuery))
                .toList();
    }

    public OrganizerAttendeeResponse updateAttendeeStatus(UUID organizerUserId, UUID eventId, UUID attendeeId, String status) {
        requireOrganizerEvent(organizerUserId, eventId);
        EventRegistration registration = registrationRepository.findByEventId(eventId).stream()
                .filter(item -> item.getAttendeeUserId().equals(attendeeId) || item.getId().equals(attendeeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attendee not found for event"));
        registration.setStatus(com.thedavelopers.eventqr.shared.constants.RegistrationStatus.valueOf(status));
        registrationRepository.save(registration);
        return toAttendee(registration, transactionLogRepository.findByEventId(eventId));
    }

    @Transactional(readOnly = true)
    public OrganizerAttendeeResponse attendee(UUID organizerUserId, UUID eventId, UUID attendeeId) {
        List<OrganizerAttendeeResponse> attendees = attendees(organizerUserId, eventId);
        return attendees.stream()
                .filter(item -> item.attendeeId().equals(attendeeId) || item.registrationId().equals(attendeeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attendee not found for event"));
    }

    @Transactional(readOnly = true)
    public List<OrganizerTransactionResponse> transactions(UUID organizerUserId, UUID eventId) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        List<ScanPurpose> purposes = scanPurposeRepository.findByEventId(eventId);
        List<EventStaffAssignment> staffAssignments = staffAssignmentRepository.findByEventId(eventId);
        return transactionLogRepository.findByEventId(eventId).stream()
                .map(log -> toTransaction(event, log, registrations, purposes, staffAssignments))
                .toList();
    }

    public TransactionResponse transaction(UUID organizerUserId, UUID eventId, UUID transactionId) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        TransactionLog log = transactionLogRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!log.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Transaction not found for event");
        }
        return new TransactionResponse(log.getId(), log.getEventId(), log.getAttendeeUserId(), log.getRegistrationId(),
                log.getQrCredentialId(), log.getScanPurposeId(), log.getTransactionType(), log.getTransactionResult(),
                log.getPointsDelta(), log.getReason(), log.getScannedAt(), event.getTitle());
    }

    @Transactional(readOnly = true)
    public OrganizerReportResponse report(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        List<TransactionLog> transactions = transactionLogRepository.findByEventId(eventId);
        long registered = registrations.size();
        long entered = registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.ENTERED).count();
        long noShows = registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.NO_SHOW).count();
        long points = pointTransactionRepository.findByEventId(eventId).stream()
                .mapToLong(com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction::getPointsChanged)
                .sum();
        if (points == 0) {
            points = transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED)
                    .mapToLong(TransactionLog::getPointsDelta).sum();
        }
        long benefitClaims = countApproved(transactions, TransactionType.BENEFIT_CLAIM);
        long boothVisits = transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                && (tx.getTransactionType() == TransactionType.BOOTH_VISIT || tx.getTransactionType() == TransactionType.SESSION_VISIT)).count();
        long redemptions = rewardRedemptionRepository.findByEventId(eventId).size()
                + countApproved(transactions, TransactionType.REWARD_REDEMPTION)
                + countApproved(transactions, TransactionType.REWARD_REDEMPTION_SCAN);
        long rejected = transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.REJECTED).count();
        return new OrganizerReportResponse(eventId, registered, entered, noShows, points, benefitClaims, boothVisits,
                redemptions, rejected,
                List.of(new ReportRow("Entry Scans", String.valueOf(countApproved(transactions, TransactionType.ENTRY))),
                        new ReportRow("Exit Scans", String.valueOf(countApproved(transactions, TransactionType.EXIT))),
                        new ReportRow("Attendance Scans", String.valueOf(countApproved(transactions, TransactionType.ATTENDANCE))),
                        new ReportRow("Rejected Scans", String.valueOf(rejected))),
                List.of(new ReportRow("Registered", String.valueOf(registered)),
                        new ReportRow("Checked In / Entered", String.valueOf(entered)),
                        new ReportRow("Exited", String.valueOf(registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.EXITED).count())),
                        new ReportRow("No Shows", String.valueOf(noShows))),
                List.of(new ReportRow("Wrong event QR", String.valueOf(countRejectedReason(transactions, "Wrong event QR"))),
                        new ReportRow("Duplicate scans", String.valueOf(countRejectedReason(transactions, "Duplicate"))),
                        new ReportRow("Other rejected scans", String.valueOf(rejected))),
                List.of(new ReportRow("Points distributed", String.valueOf(points)),
                        new ReportRow("Benefit claims", String.valueOf(benefitClaims)),
                        new ReportRow("Reward redemptions", String.valueOf(redemptions))),
                transactions.stream().limit(8)
                        .map(tx -> new ReportRow(tx.getTransactionType().name(), format(tx.getScannedAt())))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<OrganizerStaffResponse> staff(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        return staffAssignmentRepository.findByEventId(eventId).stream().map(this::toStaff).toList();
    }

    public OrganizerStaffResponse addStaff(UUID organizerUserId, UUID eventId, StaffAssignmentRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        UserProfile staffUser = resolveStaffUser(request);
        if (staffAssignmentRepository.existsByEventIdAndStaffUserId(eventId, staffUser.getId())) {
            throw new ConflictException("Staff member is already assigned to this event");
        }
        if (staffUser.getRole() == AccountRole.ATTENDEE) {
            staffUser.setRole(AccountRole.STAFF);
            userProfileRepository.save(staffUser);
        }
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setEventId(eventId);
        assignment.setStaffUserId(staffUser.getId());
        assignment.setRoleLabel(blankToDefault(request.roleLabel(), "Scanner"));
        assignment.setPermissions(String.join(",", emptyToDefault(request.permissions(), DEFAULT_PERMISSIONS)));
        assignment.setActive(true);
        assignment.setAddedByUserId(organizerUserId);
        assignment.setAddedAt(Instant.now());
        return toStaff(staffAssignmentRepository.save(assignment));
    }

    public OrganizerStaffResponse updateStaff(UUID organizerUserId, UUID eventId, UUID assignmentId,
                                              StaffAssignmentUpdateRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        EventStaffAssignment assignment = requireAssignment(eventId, assignmentId);
        if (request.active() != null) {
            assignment.setActive(request.active());
        }
        if (request.roleLabel() != null && !request.roleLabel().isBlank()) {
            assignment.setRoleLabel(request.roleLabel().trim());
        }
        if (request.permissions() != null) {
            assignment.setPermissions(String.join(",", request.permissions()));
        }
        return toStaff(staffAssignmentRepository.save(assignment));
    }

    public void removeStaff(UUID organizerUserId, UUID eventId, UUID assignmentId) {
        requireOrganizerEvent(organizerUserId, eventId);
        EventStaffAssignment assignment = requireAssignment(eventId, assignmentId);
        staffAssignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(UUID organizerUserId, String query) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return List.of();
        }
        return userProfileRepository.findTop20ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(safeQuery, safeQuery)
                .stream()
                .map(user -> new UserSearchResponse(user.getId(), user.getFullName(), user.getEmail(),
                        user.getRole().name(), user.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizerScanPurposeResponse> scanPurposes(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        List<ScanPurpose> purposes = scanPurposeRepository.findByEventId(eventId);
        if (purposes.isEmpty()) {
            return defaultScanPurposes(eventId);
        }
        return purposes.stream().map(this::toScanPurpose).toList();
    }

    public OrganizerScanPurposeResponse saveScanPurpose(UUID organizerUserId, UUID eventId,
                                                        OrganizerScanPurposeRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        validateScanPurpose(request);
        ScanPurpose purpose = request.scanPurposeId() == null
                ? scanPurposeRepository.findByEventIdAndCode(eventId, request.code()).orElseGet(ScanPurpose::new)
                : scanPurposeRepository.findById(request.scanPurposeId())
                        .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        purpose.setEventId(eventId);
        purpose.setName(request.title());
        purpose.setCode(request.code());
        purpose.setActive(request.enabled());
        purpose.setTrackingOnly(request.trackingOnly());
        purpose.setDescription(request.description());
        purpose = scanPurposeRepository.save(purpose);

        TransactionRule rule = transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purpose.getId())
                .orElseGet(TransactionRule::new);
        rule.setEventId(eventId);
        rule.setScanPurposeId(purpose.getId());
        rule.setActive(request.enabled());
        rule.setAllowDuplicate(request.allowDuplicate());
        if (rule.getId() == null) {
            rule.setDuplicateWindowMinutes(0);
            rule.setMaxUsesPerRegistration(1);
        }
        rule.setRequiresStaffAssignment(true);
        rule.setPointsAwarded(request.pointsEnabled() ? request.pointsValue() : 0);
        transactionRuleRepository.save(rule);
        return toScanPurpose(purpose);
    }

    public void deleteScanPurpose(UUID organizerUserId, UUID eventId, UUID purposeId) {
        requireOrganizerEvent(organizerUserId, eventId);
        ScanPurpose purpose = scanPurposeRepository.findById(purposeId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        if (!purpose.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Scan purpose not found for event");
        }
        scanPurposeRepository.delete(purpose);
    }

    public OrganizerScanPurposeResponse enableScanPurpose(UUID organizerUserId, UUID eventId, UUID purposeId, boolean enabled) {
        requireOrganizerEvent(organizerUserId, eventId);
        ScanPurpose purpose = scanPurposeRepository.findById(purposeId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        if (!purpose.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Scan purpose not found for event");
        }
        purpose.setActive(enabled);
        scanPurposeRepository.save(purpose);
        TransactionRule rule = transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purposeId).orElseGet(TransactionRule::new);
        rule.setEventId(eventId);
        rule.setScanPurposeId(purposeId);
        rule.setActive(enabled);
        if (rule.getId() == null) {
            rule.setDuplicateWindowMinutes(0);
            rule.setMaxUsesPerRegistration(1);
        }
        if (!enabled) {
            rule.setAllowDuplicate(false);
        }
        transactionRuleRepository.save(rule);
        return toScanPurpose(purpose);
    }

    public OrganizerScanPurposeResponse toggleTrackingOnly(UUID organizerUserId, UUID eventId, UUID purposeId, boolean trackingOnly) {
        requireOrganizerEvent(organizerUserId, eventId);
        ScanPurpose purpose = scanPurposeRepository.findById(purposeId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        if (!purpose.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Scan purpose not found for event");
        }
        purpose.setTrackingOnly(trackingOnly);
        scanPurposeRepository.save(purpose);
        TransactionRule rule = transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purposeId).orElseGet(TransactionRule::new);
        rule.setEventId(eventId);
        rule.setScanPurposeId(purposeId);
        if (trackingOnly) {
            rule.setPointsAwarded(0);
        }
        if (rule.getId() == null) {
            rule.setDuplicateWindowMinutes(0);
            rule.setMaxUsesPerRegistration(1);
        }
        transactionRuleRepository.save(rule);
        return toScanPurpose(purpose);
    }

    public List<TransactionRule> listTransactionRules(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        return transactionRuleRepository.findByEventId(eventId);
    }

    public TransactionRule saveTransactionRule(UUID organizerUserId, UUID eventId, TransactionRuleRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        TransactionRule rule = transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, request.scanPurposeId())
                .orElseGet(TransactionRule::new);
        rule.setEventId(eventId);
        rule.setScanPurposeId(request.scanPurposeId());
        rule.setActive(request.active());
        rule.setAllowDuplicate(request.allowDuplicate());
        rule.setDuplicateWindowMinutes(normalizeNonNegative(request.duplicateWindowMinutes(), 0));
        rule.setMaxUsesPerRegistration(normalizePositive(request.maxUsesPerRegistration(), 1));
        rule.setRequiresStaffAssignment(request.requiresStaffAssignment());
        rule.setPointsAwarded(request.pointsAwarded());
        return transactionRuleRepository.save(rule);
    }

    private int normalizeNonNegative(int value, int fallback) {
        return value < 0 ? fallback : value;
    }

    private int normalizePositive(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private Event requireOrganizerEvent(UUID organizerUserId, UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        UserProfile user = userProfileRepository.findById(organizerUserId)
                .orElseThrow(() -> new ForbiddenException("Organizer account not found"));
        boolean owner = organizerUserId.equals(event.getOrganizerUserId());
        if (!owner && user.getRole() != AccountRole.ADMIN) {
            throw new ForbiddenException("Organizer is not assigned to this event");
        }
        if (event.getStatus() != EventStatus.APPROVED && event.getStatus() != EventStatus.ACTIVE
                && event.getStatus() != EventStatus.ENDED) {
            throw new ForbiddenException("Event is not approved for organizer management");
        }
        return event;
    }

    private OrganizerEventResponse toOrganizerEvent(Event event) {
        UUID eventId = event.getId();
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        List<TransactionLog> transactions = transactionLogRepository.findByEventId(eventId);
        long redemptions = rewardRedemptionRepository.findByEventId(eventId).size();
        return new OrganizerEventResponse(eventId, event.getTitle(), "Organizer", formatRange(event.getEventStartAt(), event.getEventEndAt()),
                format(event.getEventStartAt()), event.getLocation(), displayStatus(event.getStatus()),
                format(event.getRegistrationOpenAt()), event.getRejectionReason(), List.of(), registrations.size(),
                registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.ENTERED).count(),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                        && tx.getTransactionType() == TransactionType.ATTENDANCE).count(),
                registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.EXITED).count(),
                registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.NO_SHOW).count(),
                transactions.size(),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED).count(),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.REJECTED).count(),
                countApproved(transactions, TransactionType.BENEFIT_CLAIM),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                        && (tx.getTransactionType() == TransactionType.BOOTH_VISIT || tx.getTransactionType() == TransactionType.SESSION_VISIT)).count(),
                redemptions + countApproved(transactions, TransactionType.REWARD_REDEMPTION)
                        + countApproved(transactions, TransactionType.REWARD_REDEMPTION_SCAN),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED)
                        .mapToLong(TransactionLog::getPointsDelta).sum(),
                "Backend status unavailable", event.isRewardsEnabled() ? "Enabled" : "Disabled",
                staffAssignmentRepository.findByEventId(eventId).size(),
                scanPurposeRepository.findByEventId(eventId).stream().filter(ScanPurpose::isActive).count());
    }

    private OrganizerAttendeeResponse toAttendee(EventRegistration registration, List<TransactionLog> logs) {
        List<TransactionLog> attendeeLogs = logs.stream()
                .filter(log -> log.getRegistrationId().equals(registration.getId()))
                .toList();
        return new OrganizerAttendeeResponse(registration.getAttendeeUserId(), registration.getId(), registration.getEventId(),
                registration.getQrCredentialId(), registration.getAttendeeName(), registration.getAttendeeEmail(),
                null, registration.getStatus().name(), eventStatus(registration),
                registration.getPointsEarned() == null ? 0 : registration.getPointsEarned(),
                attendeeLogs.stream().map(TransactionLog::getScannedAt).max(Instant::compareTo).map(this::format).orElse("-"),
                format(registration.getRegisteredAt()),
                registration.getQrCredentialId() == null ? "Pending" : "Issued",
                attendeeLogs.stream().filter(log -> log.getTransactionResult() == TransactionResult.APPROVED)
                        .map(log -> log.getTransactionType().name()).limit(5).toList(),
                attendeeLogs.stream().filter(log -> log.getTransactionResult() == TransactionResult.REJECTED)
                        .map(TransactionLog::getReason).limit(5).toList());
    }

    private OrganizerTransactionResponse toTransaction(Event event, TransactionLog log, List<EventRegistration> registrations,
                                                       List<ScanPurpose> purposes, List<EventStaffAssignment> staffAssignments) {
        EventRegistration registration = registrations.stream()
                .filter(reg -> reg.getId().equals(log.getRegistrationId()))
                .findFirst().orElse(null);
        ScanPurpose purpose = purposes.stream().filter(item -> item.getId().equals(log.getScanPurposeId())).findFirst().orElse(null);
        EventStaffAssignment staff = staffAssignments.stream()
                .filter(item -> log.getStaffUserId() != null && item.getStaffUserId().equals(log.getStaffUserId()))
                .findFirst().orElse(null);
        String staffName = staff == null ? "Staff " + (log.getStaffUserId() == null ? "unknown" : log.getStaffUserId().toString().substring(0, 8)) : toStaff(staff).name();
        String type = log.getTransactionType().name();
        return new OrganizerTransactionResponse(log.getId(), log.getEventId(), event.getTitle(), log.getAttendeeUserId(),
                registration == null ? "Attendee " + log.getAttendeeUserId().toString().substring(0, 8) : registration.getAttendeeName(),
                log.getRegistrationId(), log.getQrCredentialId(), log.getScanPurposeId(), log.getStaffUserId(), staffName,
                log.getQrCredentialId() == null ? "" : log.getQrCredentialId().toString(),
                purpose == null ? type : purpose.getName(), log.getTransactionType(),
                log.getTransactionResult(), log.getPointsDelta(), log.getReason(),
                log.getReason() == null ? type + " recorded" : log.getReason(), "Backend", purpose == null ? null : purpose.getDescription(),
                log.getScannedAt());
    }

    private OrganizerStaffResponse toStaff(EventStaffAssignment assignment) {
        UserProfile user = userProfileRepository.findById(assignment.getStaffUserId()).orElse(null);
        return new OrganizerStaffResponse(assignment.getId(), assignment.getEventId(), assignment.getStaffUserId(),
                user == null ? "Unknown staff" : user.getFullName(), user == null ? "" : user.getEmail(),
                assignment.getRoleLabel(), assignment.isActive(), splitPermissions(assignment.getPermissions()),
                assignment.getAddedAt());
    }

    private OrganizerScanPurposeResponse toScanPurpose(ScanPurpose purpose) {
        TransactionRule rule = transactionRuleRepository.findByEventIdAndScanPurposeId(purpose.getEventId(), purpose.getId()).orElse(null);
        int points = rule == null ? 0 : rule.getPointsAwarded();
        boolean allowDuplicate = rule != null && rule.isAllowDuplicate();
        return new OrganizerScanPurposeResponse(purpose.getId(), purpose.getEventId(), purpose.getName(), purpose.getDescription(),
                purpose.getCode(), purpose.isActive(), purpose.isTrackingOnly(), points > 0, points, allowDuplicate,
                allowDuplicate ? "Duplicates allowed" : defaultDuplicateRule(purpose.getCode()),
                defaultRequiredSelection(purpose.getCode()));
    }

    private List<OrganizerScanPurposeResponse> defaultScanPurposes(UUID eventId) {
        return Arrays.stream(ScanPurposeCode.values())
                .map(code -> new OrganizerScanPurposeResponse(null, eventId, defaultPurposeName(code),
                        defaultPurposeName(code), code, false, true, false, 0, false,
                        defaultDuplicateRule(code), defaultRequiredSelection(code)))
                .toList();
    }

    private UserProfile resolveStaffUser(StaffAssignmentRequest request) {
        if (request.staffUserId() != null) {
            return userProfileRepository.findById(request.staffUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff user not found"));
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Staff user ID or email is required");
        }
        return userProfileRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email " + request.email()));
    }

    private EventStaffAssignment requireAssignment(UUID eventId, UUID assignmentId) {
        EventStaffAssignment assignment = staffAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff assignment not found"));
        if (!assignment.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Staff assignment not found for event");
        }
        return assignment;
    }

    private void validateScanPurpose(OrganizerScanPurposeRequest request) {
        if (request.trackingOnly() && request.pointsEnabled()) {
            throw new BadRequestException("Tracking-only scan purposes cannot award points");
        }
        if (request.pointsValue() < 0) {
            throw new BadRequestException("Point value cannot be negative");
        }
    }

    private long countApproved(List<TransactionLog> transactions, TransactionType type) {
        return transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                && tx.getTransactionType() == type).count();
    }

    private long countRejectedReason(List<TransactionLog> transactions, String reason) {
        return transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.REJECTED
                && tx.getReason() != null && tx.getReason().toLowerCase().contains(reason.toLowerCase())).count();
    }

    private List<String> splitPermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return new ArrayList<>(DEFAULT_PERMISSIONS);
        }
        return Arrays.stream(permissions.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private List<String> emptyToDefault(List<String> value, List<String> fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String eventStatus(EventRegistration registration) {
        if (registration.getAttendedAt() != null) {
            return "Attended";
        }
        return switch (registration.getStatus()) {
            case ENTERED -> "Checked In / Entered";
            case EXITED -> "Exited";
            case NO_SHOW -> "No-show";
            case CANCELLED -> "Cancelled";
            default -> "Registered";
        };
    }

    private String displayStatus(EventStatus status) {
        return switch (status) {
            case APPROVED, ACTIVE -> "Approved";
            case ENDED -> "Completed";
            case REJECTED, CANCELLED -> "Rejected";
            default -> "Pending";
        };
    }

    private String format(Instant value) {
        return value == null ? "-" : DateTimeFormatter.ISO_INSTANT.format(value);
    }

    private String formatRange(Instant start, Instant end) {
        return format(start) + " - " + format(end);
    }

    private String defaultPurposeName(ScanPurposeCode code) {
        return switch (code) {
            case ENTRY -> "Entrance Logging";
            case ATTENDANCE -> "Attendance Recording";
            case BENEFIT_CLAIM -> "Benefit Claiming";
            case BOOTH_VISIT, SESSION_VISIT -> "Booth/Session Visit";
            case REWARD_REDEMPTION, REWARD_REDEMPTION_SCAN -> "Reward Redemption";
            case EXIT -> "Exit Logging";
            case ID_PRINT -> "ID Printing";
            case REGISTRATION_LOOKUP -> "ID Reprinting";
        };
    }

    private String defaultDuplicateRule(ScanPurposeCode code) {
        return switch (code) {
            case ENTRY -> "Prevent duplicate entry";
            case ATTENDANCE -> "Prevent duplicate attendance if configured";
            case BENEFIT_CLAIM -> "Prevent duplicate benefit claim";
            case REWARD_REDEMPTION, REWARD_REDEMPTION_SCAN -> "Prevent duplicate reward claim";
            default -> "Reject invalid duplicate scans";
        };
    }

    private String defaultRequiredSelection(ScanPurposeCode code) {
        return switch (code) {
            case BOOTH_VISIT -> "Booth";
            case SESSION_VISIT, ATTENDANCE -> "Session";
            case BENEFIT_CLAIM -> "Benefit";
            case REWARD_REDEMPTION, REWARD_REDEMPTION_SCAN -> "Reward";
            default -> "Event";
        };
    }
}
