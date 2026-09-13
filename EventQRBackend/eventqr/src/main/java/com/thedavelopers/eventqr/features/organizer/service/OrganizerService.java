package com.thedavelopers.eventqr.features.organizer.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerAttendeeResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerDashboardResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerEventResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerScanPurposeRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerScanPurposeResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerStaffResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerTransactionResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerTransactionRuleResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentUpdateRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.TransactionEntry;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.UserSearchResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.RewardSettingsRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanning.model.entity.ScanPurpose;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
@Transactional
public class OrganizerService {

    private static final List<String> DEFAULT_PERMISSIONS = List.of("Scan QR", "View attendee details");
    private static final String DEFAULT_ROLE_LABEL = "Staff";
    private static final String DEFAULT_STAFF_ROLE = "STAFF";
    private static final Logger log = LoggerFactory.getLogger(OrganizerService.class);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ScanPurposeRepository scanPurposeRepository;
    private final TransactionRuleRepository transactionRuleRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final EventStaffAssignmentRepository staffAssignmentRepository;
    private final UserProfileRepository userProfileRepository;
    private final IdTemplateRepository idTemplateRepository;
    private final NotificationService notificationService;

    public OrganizerService(EventRepository eventRepository,
                            EventRegistrationRepository registrationRepository,
                            TransactionLogRepository transactionLogRepository,
                            ScanPurposeRepository scanPurposeRepository,
                            TransactionRuleRepository transactionRuleRepository,
                            RewardRedemptionRepository rewardRedemptionRepository,
                            PointTransactionRepository pointTransactionRepository,
                            EventStaffAssignmentRepository staffAssignmentRepository,
                            UserProfileRepository userProfileRepository,
                            IdTemplateRepository idTemplateRepository,
                            NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.scanPurposeRepository = scanPurposeRepository;
        this.transactionRuleRepository = transactionRuleRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.userProfileRepository = userProfileRepository;
        this.idTemplateRepository = idTemplateRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<OrganizerEventResponse> listEvents(UUID organizerUserId) {
        return eventRepository.findByOrganizerUserId(organizerUserId).stream()
                .filter(event -> event.getStatus() == EventStatus.APPROVED || event.getStatus() == EventStatus.ACTIVE
                        || event.getStatus() == EventStatus.ENDED)
                .map(this::toOrganizerEvent)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizerEventResponse event(UUID organizerUserId, UUID eventId) {
        return toOrganizerEvent(requireOrganizerEvent(organizerUserId, eventId));
    }

    // SDD 3.5 (UC-20) — Manage Approved Event Details.
    // Reuse notes: ownership + approved-only gating live in requireOrganizerEvent (403/404 via
    // GlobalExceptionHandler); no separate OrganizerAuthorizationValidator/exception class was
    // created for this flow.
    //
    // SCOPE DEVIATION: Event Update Log Repository (SDD 3.5) omitted in MVP — tracked separately
    // for capstone defense.
    public EventResponse updateEvent(UUID organizerUserId, UUID eventId, EventRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        // SDD 3.5 / UC-20 edit lock: once an event is Active (Ongoing) or Completed the
        // Organizer can no longer edit its details — editable only while Upcoming (APPROVED).
        if (event.getStatus() == EventStatus.ACTIVE || event.getStatus() == EventStatus.ENDED) {
            throw new ConflictException("Cannot edit an event that is ongoing or completed");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("Title is required");
        }
        if (request.eventStartAt() == null) {
            throw new BadRequestException("Date is required");
        }
        if (request.capacity() == null || request.capacity() <= 0) {
            throw new BadRequestException("Capacity must be greater than 0");
        }
        long registeredCount = registrationRepository.countByEventId(eventId);
        if (request.capacity() < registeredCount) {
            throw new BadRequestException(
                    "Capacity cannot be less than current registered attendees (" + registeredCount + ")");
        }
        if (!java.util.Objects.equals(request.eventStartAt(), event.getEventStartAt())
                || !java.util.Objects.equals(request.eventEndAt(), event.getEventEndAt())
                || !java.util.Objects.equals(request.registrationOpenAt(), event.getRegistrationOpenAt())
                || !java.util.Objects.equals(request.registrationCloseAt(), event.getRegistrationCloseAt())) {
            throw new ConflictException("Registration window and event start/end dates cannot be modified after creation");
        }
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setLocation(request.location());
        event.setRegistrationOpenAt(request.registrationOpenAt());
        event.setRegistrationCloseAt(request.registrationCloseAt());
        event.setEventStartAt(request.eventStartAt());
        event.setEventEndAt(request.eventEndAt());
        event.setCapacity(request.capacity());
        event.setRewardsEnabled(Boolean.TRUE.equals(request.rewardsEnabled()));
        event.setEventLogoUrl(request.eventLogoUrl());
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getCategory(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    public EventResponse updateStatus(UUID organizerUserId, UUID eventId, EventStatus status) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        event.setStatus(status);
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getCategory(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    public EventResponse updateRewardSettings(UUID organizerUserId, UUID eventId, RewardSettingsRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        event.setRewardsEnabled(request.enabled());
        if (request.enabled()) {
            ensureRewardRedemptionScanPurpose(eventId);
        } else {
            disableRewardRedemptionScanPurpose(eventId);
        }
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getCategory(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    private void ensureRewardRedemptionScanPurpose(UUID eventId) {
        if (scanPurposeRepository.findByEventIdAndCode(eventId, ScanPurposeCode.REWARD_REDEMPTION_SCAN).isPresent()) {
            return;
        }
        ScanPurpose scanPurpose = new ScanPurpose();
        scanPurpose.setEventId(eventId);
        scanPurpose.setName("Reward Redemption");
        scanPurpose.setCode(ScanPurposeCode.REWARD_REDEMPTION_SCAN);
        scanPurpose.setActive(true);
        scanPurpose.setTrackingOnly(false);
        scanPurpose.setDescription("Staff-scan flow for redeeming attendee rewards");
        scanPurposeRepository.save(scanPurpose);
        log.info("Auto-provisioned REWARD_REDEMPTION_SCAN scan purpose for eventId={}", eventId);
    }

    private void disableRewardRedemptionScanPurpose(UUID eventId) {
        scanPurposeRepository.findByEventIdAndCode(eventId, ScanPurposeCode.REWARD_REDEMPTION_SCAN)
                .ifPresent(scanPurpose -> {
                    scanPurpose.setActive(false);
                    scanPurposeRepository.save(scanPurpose);
                    log.info("Disabled REWARD_REDEMPTION_SCAN scan purpose for eventId={} purposeId={}",
                            eventId, scanPurpose.getId());
                });
    }

    @Transactional(readOnly = true)
    public OrganizerDashboardResponse dashboard(UUID organizerUserId) {
        UserProfile organizer = userProfileRepository.findById(organizerUserId)
                .orElseThrow(() -> new ForbiddenException("Organizer account not found"));
        List<OrganizerEventResponse> events = listEvents(organizerUserId);
        long totalAttendees = events.stream().mapToLong(OrganizerEventResponse::registeredCount).sum();
        long totalTransactions = events.stream().mapToLong(OrganizerEventResponse::totalTransactions).sum();
        long totalPoints = events.stream().mapToLong(OrganizerEventResponse::totalPointsAwarded).sum();
        long rewardEvents = events.stream().filter(event -> "Enabled".equalsIgnoreCase(event.rewardsStatus())).count();
        OrganizerEventResponse firstEvent = events.isEmpty() ? null : events.get(0);
        return new OrganizerDashboardResponse(organizer.getId(), organizer.getFullName(), organizer.getEmail(), null,
                events.size(), totalAttendees, totalTransactions, totalPoints,
                rewardEvents == 0 ? "Rewards not configured" : rewardEvents + " event(s) with rewards enabled",
                events.stream().limit(5).toList(), firstEvent);
    }

    @Transactional(readOnly = true)
    public OrganizerDashboardResponse dashboard(UUID organizerUserId, UUID eventId) {
        OrganizerDashboardResponse summary = dashboard(organizerUserId);
        return new OrganizerDashboardResponse(summary.organizerUserId(), summary.organizerName(), summary.organizerEmail(),
                summary.organization(), summary.totalEvents(), summary.totalAttendees(), summary.totalTransactions(),
                summary.totalPointsAwarded(), summary.rewardsSummary(), summary.recentEvents(),
                toOrganizerEvent(requireOrganizerEvent(organizerUserId, eventId)));
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
        return transactionLogRepository.findByEventIdOrderByScannedAtDesc(eventId).stream()
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
    public List<OrganizerStaffResponse> staff(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        List<EventStaffAssignment> assignments = staffAssignmentRepository.findByEventId(eventId);
        log.debug("Organizer staff fetch eventId={} count={}", eventId, assignments.size());
        return assignments.stream().map(this::toStaff).toList();
    }

    public OrganizerStaffResponse addStaff(UUID organizerUserId, UUID eventId, StaffAssignmentRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        UserProfile staffUser = resolveStaffUser(request);
        log.debug(
                "Organizer staff add request eventId={} staffUserId={} email={}",
                eventId,
                staffUser.getId(),
                request.email());

        Optional<EventStaffAssignment> existingAssignment = staffAssignmentRepository
                .findByEventIdAndStaffUserId(eventId, staffUser.getId());
        log.debug("Organizer staff add duplicate-check eventId={} staffUserId={} exists={} active={}",
                eventId,
                staffUser.getId(),
                existingAssignment.isPresent(),
                existingAssignment.map(EventStaffAssignment::isActive).orElse(false));

        if (existingAssignment.isPresent() && existingAssignment.get().isActive()) {
            throw new ConflictException("Staff member is already assigned to this event");
        }

        boolean reactivatingExisting = existingAssignment.isPresent();
        boolean wasInactive = reactivatingExisting && !existingAssignment.get().isActive();
        log.debug("Organizer staff add path eventId={} staffUserId={} mode={}",
                eventId,
                staffUser.getId(),
                reactivatingExisting ? "REACTIVATE_EXISTING" : "CREATE_NEW");

        EventStaffAssignment assignment = existingAssignment.orElseGet(() -> {
            EventStaffAssignment created = new EventStaffAssignment();
            created.setEventId(eventId);
            created.setStaffUserId(staffUser.getId());
            created.setAddedByUserId(organizerUserId);
            created.setAddedAt(Instant.now());
            return created;
        });

        String roleLabel = normalizeRoleLabel(request.roleLabel());
        assignment.setRoleLabel(roleLabel);
        assignment.setStaffRole(toStaffRole(roleLabel));
        assignment.setPermissions(String.join(",", emptyToDefault(request.permissions(), DEFAULT_PERMISSIONS)));
        assignment.setCanScan(boolOrDefault(request.canScan(), true));
        assignment.setCanPrintId(boolOrDefault(request.canPrintId(), false));
        assignment.setCanViewLogs(boolOrDefault(request.canViewLogs(), false));
        assignment.setCanManageRewards(boolOrDefault(request.canManageRewards(), false));
        applyPermissionOverrides(assignment, request.permissions());
        if (assignment.getAddedAt() == null) {
            assignment.setAddedAt(Instant.now());
        }
        assignment.setActive(true);
        log.debug("Organizer staff add normalized eventId={} staffUserId={} roleLabel={} staffRole={} permissions={}",
                eventId,
                staffUser.getId(),
                assignment.getRoleLabel(),
                assignment.getStaffRole(),
                assignment.getPermissions());
        EventStaffAssignment saved = staffAssignmentRepository.save(assignment);
        log.debug("Organizer staff add persisted eventId={} staffUserId={} assignmentId={} active={} reactivated={}",
                eventId,
                staffUser.getId(),
                saved.getId(),
                saved.isActive(),
                existingAssignment.isPresent());

        if (staffUser.getRole() == AccountRole.ATTENDEE) {
            staffUser.setRole(AccountRole.STAFF);
            userProfileRepository.save(staffUser);
            log.debug("Organizer staff add upgraded role eventId={} staffUserId={} fromRole=ATTENDEE toRole=STAFF",
                    eventId, staffUser.getId());
        } else {
            log.debug("Organizer staff add kept role eventId={} staffUserId={} role={}",
                    eventId, staffUser.getId(), staffUser.getRole());
        }

        if (!reactivatingExisting || wasInactive) {
            UserProfile organizerProfile = userProfileRepository.findById(organizerUserId).orElse(null);
            String organizerName = organizerProfile == null ? "Organizer" : organizerProfile.getFullName();
            try {
                notificationService.createStaffAssignmentNotification(
                        eventId, staffUser.getId(), event.getTitle(), organizerName);
                log.debug("Staff assignment notification created eventId={} staffUserId={}", eventId, staffUser.getId());
            } catch (Exception ex) {
                log.error("Failed to create staff assignment notification eventId={} staffUserId={}", eventId, staffUser.getId(), ex);
            }
        }

        return toStaff(saved);
    }

    public OrganizerStaffResponse updateStaff(UUID organizerUserId, UUID eventId, UUID assignmentId,
                                              StaffAssignmentUpdateRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        EventStaffAssignment assignment = requireAssignment(eventId, assignmentId);
        if (request.active() != null) {
            assignment.setActive(request.active());
        }
        if (request.roleLabel() != null && !request.roleLabel().isBlank()) {
            String roleLabel = normalizeRoleLabel(request.roleLabel());
            assignment.setRoleLabel(roleLabel);
            assignment.setStaffRole(toStaffRole(roleLabel));
        }
        if (request.canScan() != null) {
            assignment.setCanScan(request.canScan());
        }
        if (request.canPrintId() != null) {
            assignment.setCanPrintId(request.canPrintId());
        }
        if (request.canViewLogs() != null) {
            assignment.setCanViewLogs(request.canViewLogs());
        }
        if (request.canManageRewards() != null) {
            assignment.setCanManageRewards(request.canManageRewards());
        }
        if (request.permissions() != null) {
            assignment.setPermissions(String.join(",", request.permissions()));
            applyPermissionOverrides(assignment, request.permissions());
        }
        return toStaff(staffAssignmentRepository.save(assignment));
    }

    public void removeStaff(UUID organizerUserId, UUID eventId, UUID assignmentId) {
        Event event = requireOrganizerEvent(organizerUserId, eventId);
        EventStaffAssignment assignment = staffAssignmentRepository.findById(assignmentId)
                .filter(item -> item.getEventId().equals(eventId))
                .or(() -> staffAssignmentRepository.findByEventIdAndStaffUserId(eventId, assignmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Staff assignment not found for event"));

        boolean wasActive = assignment.isActive();
        assignment.setActive(false);
        staffAssignmentRepository.save(assignment);

        if (wasActive) {
            UserProfile organizerProfile = userProfileRepository.findById(organizerUserId).orElse(null);
            String organizerName = organizerProfile == null ? "Organizer" : organizerProfile.getFullName();
            try {
                notificationService.createStaffRemovalNotification(
                        eventId, assignment.getStaffUserId(), event.getTitle(), organizerName);
                log.debug("Staff removal notification created eventId={} staffUserId={}", eventId, assignment.getStaffUserId());
            } catch (Exception ex) {
                log.error("Failed to create staff removal notification eventId={} staffUserId={}", eventId, assignment.getStaffUserId(), ex);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(UUID organizerUserId, String query) {
        userProfileRepository.findById(organizerUserId)
                .orElseThrow(() -> new ForbiddenException("Organizer account not found"));
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
    public List<UserSearchResponse> searchUsers(UUID organizerUserId, UUID eventId, String query) {
        requireOrganizerEvent(organizerUserId, eventId);
        return searchUsers(organizerUserId, query);
    }

    @Transactional(readOnly = true)
    public List<OrganizerScanPurposeResponse> scanPurposes(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        List<ScanPurpose> purposes = scanPurposeRepository.findByEventId(eventId);
        log.debug("ScanPurposePersistence eventId={} loadedCount={} names={}", eventId, purposes.size(), summarizeScanPurposeNames(purposes));
        if (purposes.isEmpty()) {
            return defaultScanPurposes(eventId);
        }
        return purposes.stream().map(this::toScanPurpose).toList();
    }

    public OrganizerScanPurposeResponse saveScanPurpose(UUID organizerUserId, UUID eventId,
                                                        OrganizerScanPurposeRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        validateScanPurpose(request);
        boolean creating = request.scanPurposeId() == null;
        ScanPurpose purpose = creating
            ? new ScanPurpose()
            : scanPurposeRepository.findById(request.scanPurposeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        if (purpose.getId() != null && !purpose.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Scan purpose not found for event");
        }
        purpose.setEventId(eventId);
        purpose.setName(request.title());
        purpose.setCode(request.code());
        purpose.setActive(request.enabled());
        purpose.setTrackingOnly(request.trackingOnly());
        purpose.setDescription(request.description());
        purpose = scanPurposeRepository.save(purpose);
        log.debug("ScanPurposePersistence eventId={} action={} purposeId={} name={} code={} enabled={} trackingOnly={}",
            eventId, creating ? "create" : "update", purpose.getId(), purpose.getName(), purpose.getCode(),
            purpose.isActive(), purpose.isTrackingOnly());

        TransactionRule rule = transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purpose.getId())
                .orElseGet(TransactionRule::new);
        rule.setEventId(eventId);
        rule.setScanPurposeId(purpose.getId());
        rule.setActive(request.enabled());
        rule.setAllowDuplicate(request.allowDuplicate());
        if (rule.getId() == null) {
            rule.setDuplicateWindowMinutes(0);
            rule.setMaxUsesPerRegistration(1);
            rule.setRequiresStaffAssignment(true);
        }
        rule.setPointsAwarded(request.pointsEnabled() ? request.pointsValue() : 0);
        transactionRuleRepository.save(rule);
        OrganizerScanPurposeResponse response = toScanPurpose(purpose);
        log.debug("ScanPurposePersistence eventId={} backendResponse purposeId={} name={} code={} enabled={} trackingOnly={} pointsEnabled={} pointsValue={}",
            eventId, response.scanPurposeId(), response.title(), response.code(), response.enabled(), response.trackingOnly(),
            response.pointsEnabled(), response.pointsValue());
        return response;
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
        OrganizerScanPurposeResponse response = toScanPurpose(purpose);
        log.debug("ScanPurposePersistence eventId={} action=toggle purposeId={} enabled={} backendResponseName={} code={}",
                eventId, purposeId, enabled, response.title(), response.code());
        return response;
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
        OrganizerScanPurposeResponse response = toScanPurpose(purpose);
        log.debug("ScanPurposePersistence eventId={} action=trackingOnly purposeId={} trackingOnly={} backendResponseName={} code={}",
                eventId, purposeId, trackingOnly, response.title(), response.code());
        return response;
    }

    public List<OrganizerTransactionRuleResponse> listTransactionRules(UUID organizerUserId, UUID eventId) {
        requireOrganizerEvent(organizerUserId, eventId);
        return transactionRuleRepository.findByEventId(eventId).stream().map(this::toTransactionRule).toList();
    }

    public OrganizerTransactionRuleResponse saveTransactionRule(UUID organizerUserId, UUID eventId, TransactionRuleRequest request) {
        return saveTransactionRule(organizerUserId, eventId, null, request);
    }

    public OrganizerTransactionRuleResponse saveTransactionRule(UUID organizerUserId, UUID eventId, UUID ruleId, TransactionRuleRequest request) {
        requireOrganizerEvent(organizerUserId, eventId);
        ScanPurpose purpose = scanPurposeRepository.findById(request.scanPurposeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        if (!purpose.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Scan purpose not found for event");
        }
        TransactionRule rule = ruleId == null
                ? transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, request.scanPurposeId())
                        .orElseGet(TransactionRule::new)
                : transactionRuleRepository.findById(ruleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Transaction rule not found"));
        if (rule.getId() != null && !rule.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Transaction rule not found for event");
        }
        rule.setEventId(eventId);
        rule.setScanPurposeId(request.scanPurposeId());
        rule.setActive(request.active());
        rule.setAllowDuplicate(request.allowDuplicate());
        rule.setDuplicateWindowMinutes(normalizeNonNegative(request.duplicateWindowMinutes(), 0));
        rule.setMaxUsesPerRegistration(normalizePositive(request.maxUsesPerRegistration(), 1));
        rule.setRequiresStaffAssignment(request.requiresStaffAssignment());
        rule.setPointsAwarded(request.pointsAwarded());
        return toTransactionRule(transactionRuleRepository.save(rule));
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
        if (!owner) {
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
        long redemptions = rewardRedemptionRepository.findByEventId(eventId).stream()
                .filter(redemption -> redemption.getStatus() == RedemptionStatus.REDEEMED).count();
        int capacity = event.getCapacity() == null ? 0 : event.getCapacity();
        // Registered = registrations excluding CANCELLED and NO_SHOW — must stay in sync with DashboardService canonical count
        int currentAttendeeCount = (int) registrations.stream()
                .filter(reg -> reg.getStatus().isCountedAsRegistered())
                .count();
        return new OrganizerEventResponse(eventId, event.getTitle(), "Organizer", formatRange(event.getEventStartAt(), event.getEventEndAt()),
                format(event.getEventStartAt()), event.getLocation(), displayStatus(event.getStatus()),
                format(event.getRegistrationOpenAt()), event.getRejectionReason(), event.getDescription(),
                event.getEventStartAt(), event.getEventEndAt(), event.getRegistrationOpenAt(), event.getRegistrationCloseAt(),
                capacity, currentAttendeeCount, Math.max(0, capacity - currentAttendeeCount), List.of(), (long) currentAttendeeCount,
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
                redemptions,
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED)
                        .mapToLong(TransactionLog::getPointsDelta).sum(),
                idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId).isPresent() ? "Configured" : "Not configured",
                event.isRewardsEnabled() ? "Enabled" : "Disabled",
                staffAssignmentRepository.findByEventId(eventId).size(),
                scanPurposeRepository.findByEventId(eventId).stream().filter(ScanPurpose::isActive).count(),
                event.isRewardsEnabled(), event.getOrganizerUserId(), event.getEventLogoUrl());
    }

    private OrganizerAttendeeResponse toAttendee(EventRegistration registration, List<TransactionLog> logs) {
        List<TransactionLog> attendeeLogs = logs.stream()
                .filter(log -> log.getRegistrationId().equals(registration.getId()))
                .sorted(Comparator.comparing(TransactionLog::getScannedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new OrganizerAttendeeResponse(registration.getAttendeeUserId(), registration.getId(), registration.getEventId(),
                registration.getQrCredentialId(), registration.getAttendeeName(), registration.getAttendeeEmail(),
                null, registration.getStatus().name(), eventStatus(registration),
                registration.getPointsEarned() == null ? 0 : registration.getPointsEarned(),
                attendeeLogs.stream().map(TransactionLog::getScannedAt).max(Instant::compareTo).map(this::format).orElse("-"),
                format(registration.getRegisteredAt()),
                registration.getQrCredentialId() == null ? "Pending" : "Issued",
                attendeeLogs.stream().filter(log -> log.getTransactionResult() == TransactionResult.APPROVED)
                        .map(log -> new TransactionEntry(log.getTransactionType().name(), format(log.getScannedAt())))
                        .limit(5).toList(),
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
        UserProfile staffProfile = staff == null ? null : userProfileRepository.findById(staff.getStaffUserId()).orElse(null);
        String staffName = staff == null ? "Staff " + (log.getStaffUserId() == null ? "unknown" : log.getStaffUserId().toString().substring(0, 8)) : toStaff(staff).name();
        String type = log.getTransactionType().name();
        return new OrganizerTransactionResponse(log.getId(), log.getEventId(), event.getTitle(), log.getAttendeeUserId(),
            registration == null ? "Attendee " + log.getAttendeeUserId().toString().substring(0, 8) : registration.getAttendeeName(),
            registration == null ? null : registration.getAttendeeEmail(),
            log.getRegistrationId(), log.getQrCredentialId(), log.getScanPurposeId(), log.getStaffUserId(), staffName,
            staffProfile == null ? null : staffProfile.getEmail(),
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
                resolveRoleLabel(assignment), assignment.isActive(), assignment.isCanScan(), assignment.isCanPrintId(),
                assignment.isCanViewLogs(), assignment.isCanManageRewards(), splitPermissions(assignment.getPermissions()),
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

    private OrganizerTransactionRuleResponse toTransactionRule(TransactionRule rule) {
        return new OrganizerTransactionRuleResponse(rule.getId(), rule.getEventId(), rule.getScanPurposeId(),
                rule.isActive(), rule.isAllowDuplicate(), rule.getDuplicateWindowMinutes(),
                rule.getMaxUsesPerRegistration(), rule.isRequiresStaffAssignment(), rule.getPointsAwarded(),
                rule.getCreatedAt(), rule.getUpdatedAt());
    }

    private List<OrganizerScanPurposeResponse> defaultScanPurposes(UUID eventId) {
        return Arrays.stream(ScanPurposeCode.values())
                .map(code -> new OrganizerScanPurposeResponse(null, eventId, defaultPurposeName(code),
                        defaultPurposeName(code), code, false, true, false, 0, false,
                        defaultDuplicateRule(code), defaultRequiredSelection(code)))
                .toList();
    }

        private String summarizeScanPurposeNames(List<ScanPurpose> purposes) {
        return purposes.stream()
            .map(purpose -> purpose.getName() + "[" + purpose.getCode() + "]")
            .toList()
            .toString();
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

    private boolean boolOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeRoleLabel(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ROLE_LABEL;
        }
        String normalized = value.trim();
        return switch (normalized.toUpperCase()) {
            case "SCANNER", "REGISTRATION_STAFF", "ID_PRINTER", "REWARD_STAFF", "EVENT_MANAGER", "STAFF" -> DEFAULT_ROLE_LABEL;
            default -> normalized;
        };
    }

    private String toStaffRole(String roleLabel) {
        return DEFAULT_STAFF_ROLE;
    }

    private String resolveRoleLabel(EventStaffAssignment assignment) {
        String roleLabel = assignment.getRoleLabel();
        if (roleLabel == null || roleLabel.isBlank()) {
            return DEFAULT_ROLE_LABEL;
        }
        String normalized = roleLabel.trim();
        return switch (normalized.toUpperCase()) {
            case "SCANNER", "REGISTRATION_STAFF", "ID_PRINTER", "REWARD_STAFF", "EVENT_MANAGER", "STAFF" -> DEFAULT_ROLE_LABEL;
            default -> normalized;
        };
    }

    private void applyPermissionOverrides(EventStaffAssignment assignment, List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        String normalized = String.join("|", permissions).toLowerCase();
        assignment.setCanScan(normalized.contains("scan"));
        assignment.setCanPrintId(normalized.contains("print"));
        assignment.setCanViewLogs(normalized.contains("log"));
        assignment.setCanManageRewards(normalized.contains("reward"));
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
            case APPROVED -> "Approved";
            // ACTIVE is shown as its own lifecycle label so the client can distinguish an
            // Upcoming (APPROVED) event from one that is ongoing and therefore edit-locked.
            case ACTIVE -> "Active";
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

