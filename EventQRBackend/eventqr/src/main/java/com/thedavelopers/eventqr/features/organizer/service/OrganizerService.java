package com.thedavelopers.eventqr.features.organizer.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
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
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
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
import com.thedavelopers.eventqr.shared.constants.AccountRoles;
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
    private final RegistrationService registrationService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String EVENTS_CACHE = "events";
    private static final String REGISTRATIONS_CACHE = "registrations";
    private static final String SCAN_PURPOSES_CACHE = "scan-purposes";

    /** Attendee status moves an organizer may make by hand. CANCELLED goes through RegistrationService.cancel. */
    private static final Map<RegistrationStatus, java.util.Set<RegistrationStatus>> ATTENDEE_TRANSITIONS = Map.of(
            RegistrationStatus.REGISTERED, java.util.Set.of(RegistrationStatus.ENTERED, RegistrationStatus.NO_SHOW, RegistrationStatus.CANCELLED),
            RegistrationStatus.ENTERED, java.util.Set.of(RegistrationStatus.EXITED),
            RegistrationStatus.EXITED, java.util.Set.of(RegistrationStatus.ENTERED),
            RegistrationStatus.NO_SHOW, java.util.Set.of(RegistrationStatus.REGISTERED),
            RegistrationStatus.CANCELLED, java.util.Set.of());

    /** Event lifecycle moves an organizer may make. ENDED is terminal. */
    private static final Map<EventStatus, java.util.Set<EventStatus>> EVENT_TRANSITIONS = Map.of(
            EventStatus.APPROVED, java.util.Set.of(EventStatus.ACTIVE, EventStatus.ENDED, EventStatus.CANCELLED),
            EventStatus.ACTIVE, java.util.Set.of(EventStatus.ENDED, EventStatus.CANCELLED),
            EventStatus.ENDED, java.util.Set.of());

    @org.springframework.beans.factory.annotation.Autowired
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
                            NotificationService notificationService,
                            RegistrationService registrationService) {
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
        this.registrationService = registrationService;
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
    public OrganizerEventResponse event(UUID organizerUserId, UUID eventId, AccountRole role) {
        return toOrganizerEvent(requireOrganizerEvent(organizerUserId, eventId, role));
    }

    // SDD 3.5 (UC-20) — Manage Approved Event Details.
    // Reuse notes: ownership + approved-only gating live in requireOrganizerEvent (403/404 via
    // GlobalExceptionHandler); no separate OrganizerAuthorizationValidator/exception class was
    // created for this flow.
    //
    // SCOPE DEVIATION: Event Update Log Repository (SDD 3.5) omitted in MVP — tracked separately
    // for capstone defense.
    @CacheEvict(cacheNames = EVENTS_CACHE, allEntries = true)
    public EventResponse updateEvent(UUID organizerUserId, UUID eventId, AccountRole role, EventRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
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
        // Same definition as the Hub "Registered" tile: excludes CANCELLED and NO_SHOW.
        long registeredCount = registrationRepository.findByEventId(eventId).stream()
                .filter(reg -> reg.getStatus().isCountedAsRegistered())
                .count();
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

    @CacheEvict(cacheNames = EVENTS_CACHE, allEntries = true)
    public EventResponse updateStatus(UUID organizerUserId, UUID eventId, AccountRole role, EventStatus status) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
        if (status == null) {
            throw new BadRequestException("Event status is required");
        }
        if (status != event.getStatus()) {
            java.util.Set<EventStatus> allowed = EVENT_TRANSITIONS.getOrDefault(event.getStatus(), java.util.Set.of());
            if (!allowed.contains(status)) {
                throw new BadRequestException("Cannot change event status from " + event.getStatus() + " to " + status
                        + (allowed.isEmpty() ? "" : ". Allowed: " + allowed));
            }
        }
        event.setStatus(status);
        return new EventResponse(eventRepository.save(event).getId(), event.getTitle(), event.getDescription(), event.getCategory(), event.getLocation(),
                event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), event.getEventStartAt(), event.getEventEndAt(),
                event.getCapacity(), event.getCurrentAttendeeCount(), event.getStatus(), event.isRewardsEnabled(),
                event.getOrganizerUserId(), event.getApprovedByUserId(), event.getApprovedAt(), event.getRejectionReason());
    }

    @CacheEvict(cacheNames = {EVENTS_CACHE, SCAN_PURPOSES_CACHE}, allEntries = true)
    public EventResponse updateRewardSettings(UUID organizerUserId, UUID eventId, AccountRole role, RewardSettingsRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
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
        long totalRedemptions = events.stream().mapToLong(OrganizerEventResponse::rewardRedemptions).sum();
        long rewardEvents = events.stream().filter(event -> "Enabled".equalsIgnoreCase(event.rewardsStatus())).count();
        OrganizerEventResponse firstEvent = events.isEmpty() ? null : events.get(0);
        return new OrganizerDashboardResponse(organizer.getId(), organizer.getFullName(), organizer.getEmail(), null,
                events.size(), totalAttendees, totalTransactions, totalPoints,
                rewardEvents == 0 ? "Rewards not configured" : rewardEvents + " event(s) with rewards enabled",
                events.stream().limit(5).toList(), firstEvent, totalAttendees, totalRedemptions);
    }

    @Transactional(readOnly = true)
    public OrganizerDashboardResponse dashboard(UUID organizerUserId, UUID eventId, AccountRole role) {
        OrganizerDashboardResponse summary = dashboard(organizerUserId);
        return new OrganizerDashboardResponse(summary.organizerUserId(), summary.organizerName(), summary.organizerEmail(),
                summary.organization(), summary.totalEvents(), summary.totalAttendees(), summary.totalTransactions(),
                summary.totalPointsAwarded(), summary.rewardsSummary(), summary.recentEvents(),
                toOrganizerEvent(requireOrganizerEvent(organizerUserId, eventId, role)),
                summary.totalRegistrations(), summary.rewardRedemptions());
    }

    @Transactional(readOnly = true)
    public List<OrganizerAttendeeResponse> attendees(UUID organizerUserId, UUID eventId, AccountRole role) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        List<TransactionLog> logs = transactionLogRepository.findByEventId(eventId);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        Map<UUID, Integer> points = earnedPoints(eventId, registrations);
        return registrations.stream()
                .map(registration -> toAttendee(registration, logs, points.getOrDefault(registration.getAttendeeUserId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizerAttendeeResponse> searchAttendees(UUID organizerUserId, UUID eventId, AccountRole role, String query) {
        String safeQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (safeQuery.isBlank()) {
            return attendees(organizerUserId, eventId, role);
        }
        return attendees(organizerUserId, eventId, role).stream()
                .filter(attendee -> attendee.name().toLowerCase(java.util.Locale.ROOT).contains(safeQuery)
                        || attendee.email().toLowerCase(java.util.Locale.ROOT).contains(safeQuery)
                        || attendee.registrationStatus().toLowerCase(java.util.Locale.ROOT).contains(safeQuery)
                        || attendee.currentEventStatus().toLowerCase(java.util.Locale.ROOT).contains(safeQuery))
                .toList();
    }

    @CacheEvict(cacheNames = {EVENTS_CACHE, REGISTRATIONS_CACHE}, allEntries = true)
    public OrganizerAttendeeResponse updateAttendeeStatus(UUID organizerUserId, UUID eventId, AccountRole role, UUID attendeeId, String status) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
        RegistrationStatus target = parseAttendeeStatus(status);
        EventRegistration registration = registrationRepository.findByEventId(eventId).stream()
                .filter(item -> item.getAttendeeUserId().equals(attendeeId) || item.getId().equals(attendeeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attendee not found for event"));
        RegistrationStatus current = registration.getStatus();
        if (current != target) {
            java.util.Set<RegistrationStatus> allowed = ATTENDEE_TRANSITIONS.getOrDefault(current, java.util.Set.of());
            if (!allowed.contains(target)) {
                throw new BadRequestException("Cannot change attendee status from " + current + " to " + target
                        + (allowed.isEmpty() ? " (" + current + " is final)" : ". Allowed: " + allowed));
            }
            if (target == RegistrationStatus.CANCELLED) {
                // Shared cancel logic: guarded transition, seat release, QR deactivation.
                registrationService.cancel(registration.getId(), registration.getAttendeeUserId());
                // Only the organizer path notifies; an attendee cancelling their own registration does not.
                notificationService.createRegistrationCancelledByOrganizerNotification(
                        eventId, registration.getAttendeeUserId(), event.getTitle());
            } else {
                applyAttendeeTransition(registration, current, target);
            }
            entityManager.flush();
            entityManager.clear();
            registration = registrationRepository.findById(registration.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Attendee not found for event"));
        }
        return toAttendee(registration, transactionLogRepository.findByEventId(eventId),
                earnedPoints(eventId, List.of(registration)).getOrDefault(registration.getAttendeeUserId(), 0));
    }

    private RegistrationStatus parseAttendeeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Attendee status is required");
        }
        try {
            return RegistrationStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid attendee status '" + status + "'. Allowed: "
                    + Arrays.toString(RegistrationStatus.values()));
        }
    }

    /**
     * Guarded status move (loses cleanly to a concurrent change), keeps events.current_attendee_count aligned with
     * {@link RegistrationStatus#isCountedAsRegistered()} (CANCELLED and NO_SHOW free their seat) and stamps
     * entered/exited timestamps.
     */
    private void applyAttendeeTransition(EventRegistration registration, RegistrationStatus current, RegistrationStatus target) {
        int updated = registrationRepository.updateStatusIfCurrent(registration.getId(), current.name(), target.name());
        if (updated == 0) {
            throw new ConflictException("Attendee status changed in the meantime. Refresh and try again.");
        }
        boolean wasCounted = current.isCountedAsRegistered();
        boolean isCounted = target.isCountedAsRegistered();
        if (wasCounted && !isCounted) {
            eventRepository.decrementAttendeeCount(registration.getEventId());
        } else if (!wasCounted && isCounted && eventRepository.incrementAttendeeCountIfAvailable(registration.getEventId()) == 0) {
            throw new ConflictException("Event is at capacity");
        }
        if (target == RegistrationStatus.ENTERED || target == RegistrationStatus.EXITED) {
            entityManager.flush();
            entityManager.clear();
            EventRegistration fresh = registrationRepository.findById(registration.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Attendee not found for event"));
            if (target == RegistrationStatus.ENTERED) {
                // EXITED -> ENTERED is a re-entry: keep the original first-entry timestamp.
                if (fresh.getEnteredAt() == null) {
                    fresh.setEnteredAt(Instant.now());
                }
            } else {
                fresh.setExitedAt(Instant.now());
            }
            registrationRepository.save(fresh);
        }
    }

    @Transactional(readOnly = true)
    public OrganizerAttendeeResponse attendee(UUID organizerUserId, UUID eventId, AccountRole role, UUID attendeeId) {
        List<OrganizerAttendeeResponse> attendees = attendees(organizerUserId, eventId, role);
        return attendees.stream()
                .filter(item -> item.attendeeId().equals(attendeeId) || item.registrationId().equals(attendeeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attendee not found for event"));
    }

    @Transactional(readOnly = true)
    public List<OrganizerTransactionResponse> transactions(UUID organizerUserId, UUID eventId, AccountRole role) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        List<ScanPurpose> purposes = scanPurposeRepository.findByEventId(eventId);
        List<EventStaffAssignment> staffAssignments = staffAssignmentRepository.findByEventId(eventId);
        return transactionLogRepository.findByEventIdOrderByScannedAtDesc(eventId).stream()
                .map(log -> toTransaction(event, log, registrations, purposes, staffAssignments))
                .toList();
    }

    public TransactionResponse transaction(UUID organizerUserId, UUID eventId, AccountRole role, UUID transactionId) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
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
    public List<OrganizerStaffResponse> staff(UUID organizerUserId, UUID eventId, AccountRole role) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        List<EventStaffAssignment> assignments = staffAssignmentRepository.findByEventId(eventId);
        log.debug("Organizer staff fetch eventId={} count={}", eventId, assignments.size());
        return assignments.stream().map(this::toStaff).toList();
    }

    public OrganizerStaffResponse addStaff(UUID organizerUserId, UUID eventId, AccountRole role, StaffAssignmentRequest request) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
        validatePermissionTokens(request.permissions());
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
        // Defaults: canScan=true, everything else false; a permissions list overrides the defaults and
        // explicit boolean flags override both.
        assignment.setCanScan(true);
        assignment.setCanPrintId(false);
        assignment.setCanViewLogs(false);
        assignment.setCanManageRewards(false);
        applyPermissionOverrides(assignment, request.permissions());
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

        boolean promotedToStaff = false;
        if (staffUser.getRole() == AccountRole.ATTENDEE) {
            promotedToStaff = true;
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

        OrganizerStaffResponse response = toStaff(saved);
        return new OrganizerStaffResponse(response.assignmentId(), response.eventId(), response.staffUserId(),
                response.name(), response.email(), response.roleLabel(), response.active(), response.canScan(),
                response.canPrintId(), response.canViewLogs(), response.canManageRewards(), response.permissions(),
                response.addedAt(), promotedToStaff);
    }

    public OrganizerStaffResponse updateStaff(UUID organizerUserId, UUID eventId, AccountRole role, UUID assignmentId,
                                              StaffAssignmentUpdateRequest request) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        validatePermissionTokens(request.permissions());
        EventStaffAssignment assignment = requireAssignment(eventId, assignmentId);
        if (request.active() != null) {
            assignment.setActive(request.active());
        }
        if (request.roleLabel() != null && !request.roleLabel().isBlank()) {
            String roleLabel = normalizeRoleLabel(request.roleLabel());
            assignment.setRoleLabel(roleLabel);
            assignment.setStaffRole(toStaffRole(roleLabel));
        }
        if (request.permissions() != null) {
            assignment.setPermissions(String.join(",", request.permissions()));
            applyPermissionOverrides(assignment, request.permissions());
        }
        // Explicit flags are applied last so they win over a permissions list in the same request.
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
        EventStaffAssignment saved = staffAssignmentRepository.save(assignment);
        if (request.active() != null) {
            syncStaffRoleAfterAssignmentChange(assignment.getStaffUserId());
        }
        return toStaff(saved);
    }

    public void removeStaff(UUID organizerUserId, UUID eventId, AccountRole role, UUID assignmentId) {
        Event event = requireOrganizerEvent(organizerUserId, eventId, role);
        EventStaffAssignment assignment = staffAssignmentRepository.findById(assignmentId)
                .filter(item -> item.getEventId().equals(eventId))
                .or(() -> staffAssignmentRepository.findByEventIdAndStaffUserId(eventId, assignmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Staff assignment not found for event"));

        boolean wasActive = assignment.isActive();
        assignment.setActive(false);
        staffAssignmentRepository.save(assignment);
        syncStaffRoleAfterAssignmentChange(assignment.getStaffUserId());

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

    private void syncStaffRoleAfterAssignmentChange(UUID staffUserId) {
        boolean hasActive = staffAssignmentRepository.existsByStaffUserIdAndActiveTrue(staffUserId);
        userProfileRepository.findById(staffUserId).ifPresent(user -> {
            if (!hasActive && user.getRole() == AccountRole.STAFF) {
                user.setRole(AccountRole.ATTENDEE);
                userProfileRepository.save(user);
                log.info("Demoted user {} to ATTENDEE after last active staff assignment was removed/deactivated", staffUserId);
            } else if (hasActive && user.getRole() == AccountRole.ATTENDEE) {
                user.setRole(AccountRole.STAFF);
                userProfileRepository.save(user);
                log.info("Promoted user {} to STAFF after activating staff assignment", staffUserId);
            }
        });
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
    public List<UserSearchResponse> searchUsers(UUID organizerUserId, UUID eventId, AccountRole role, String query) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        return searchUsers(organizerUserId, query);
    }

    @Transactional(readOnly = true)
    public List<OrganizerScanPurposeResponse> scanPurposes(UUID organizerUserId, UUID eventId, AccountRole role) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        List<ScanPurpose> purposes = scanPurposeRepository.findByEventId(eventId);
        log.debug("ScanPurposePersistence eventId={} loadedCount={} names={}", eventId, purposes.size(), summarizeScanPurposeNames(purposes));
        if (purposes.isEmpty()) {
            return defaultScanPurposes(eventId);
        }
        return purposes.stream().map(this::toScanPurpose).toList();
    }

    @CacheEvict(cacheNames = {SCAN_PURPOSES_CACHE}, allEntries = true)
    public OrganizerScanPurposeResponse saveScanPurpose(UUID organizerUserId, UUID eventId, AccountRole role,
                                                        OrganizerScanPurposeRequest request) {
        requireOrganizerEvent(organizerUserId, eventId, role);
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
        // "Allow duplicates" must actually allow them: max uses 0 = unlimited (see
        // TransactionService.determineDuplicateReason). A custom limit (>1) set via the rule editor is kept.
        if (!request.allowDuplicate()) {
            rule.setMaxUsesPerRegistration(1);
            rule.setDuplicateWindowMinutes(0);
        } else if (rule.getMaxUsesPerRegistration() == 1) {
            rule.setMaxUsesPerRegistration(0);
        }
        rule.setPointsAwarded(request.pointsEnabled() ? request.pointsValue() : 0);
        transactionRuleRepository.save(rule);
        OrganizerScanPurposeResponse response = toScanPurpose(purpose);
        log.debug("ScanPurposePersistence eventId={} backendResponse purposeId={} name={} code={} enabled={} trackingOnly={} pointsEnabled={} pointsValue={}",
            eventId, response.scanPurposeId(), response.title(), response.code(), response.enabled(), response.trackingOnly(),
            response.pointsEnabled(), response.pointsValue());
        return response;
    }

    @CacheEvict(cacheNames = {SCAN_PURPOSES_CACHE}, allEntries = true)
    public void deleteScanPurpose(UUID organizerUserId, UUID eventId, AccountRole role, UUID purposeId) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        ScanPurpose purpose = scanPurposeRepository.findById(purposeId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found"));
        if (!purpose.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Scan purpose not found for event");
        }
        if (transactionLogRepository.existsByScanPurposeId(purposeId)) {
            throw new ConflictException("Scan purpose cannot be deleted because transaction logs exist");
        }
        transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purposeId)
                .ifPresent(transactionRuleRepository::delete);
        scanPurposeRepository.delete(purpose);
    }

    @CacheEvict(cacheNames = {SCAN_PURPOSES_CACHE}, allEntries = true)
    public OrganizerScanPurposeResponse enableScanPurpose(UUID organizerUserId, UUID eventId, AccountRole role, UUID purposeId, boolean enabled) {
        requireOrganizerEvent(organizerUserId, eventId, role);
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
        // allowDuplicate is a separate setting: toggling a purpose on/off must not change it.
        transactionRuleRepository.save(rule);
        OrganizerScanPurposeResponse response = toScanPurpose(purpose);
        log.debug("ScanPurposePersistence eventId={} action=toggle purposeId={} enabled={} backendResponseName={} code={}",
                eventId, purposeId, enabled, response.title(), response.code());
        return response;
    }

    @CacheEvict(cacheNames = {SCAN_PURPOSES_CACHE}, allEntries = true)
    public OrganizerScanPurposeResponse toggleTrackingOnly(UUID organizerUserId, UUID eventId, AccountRole role, UUID purposeId, boolean trackingOnly) {
        requireOrganizerEvent(organizerUserId, eventId, role);
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

    public List<OrganizerTransactionRuleResponse> listTransactionRules(UUID organizerUserId, UUID eventId, AccountRole role) {
        requireOrganizerEvent(organizerUserId, eventId, role);
        return transactionRuleRepository.findByEventId(eventId).stream().map(this::toTransactionRule).toList();
    }

    public OrganizerTransactionRuleResponse saveTransactionRule(UUID organizerUserId, UUID eventId, AccountRole role, TransactionRuleRequest request) {
        return saveTransactionRule(organizerUserId, eventId, role, null, request);
    }

    @CacheEvict(cacheNames = {SCAN_PURPOSES_CACHE}, allEntries = true)
    public OrganizerTransactionRuleResponse saveTransactionRule(UUID organizerUserId, UUID eventId, AccountRole role, UUID ruleId, TransactionRuleRequest request) {
        requireOrganizerEvent(organizerUserId, eventId, role);
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
        // 0 = unlimited uses, only meaningful when duplicates are allowed; otherwise a single use.
        rule.setMaxUsesPerRegistration(request.allowDuplicate()
                ? normalizeNonNegative(request.maxUsesPerRegistration(), 0) : 1);
        rule.setRequiresStaffAssignment(request.requiresStaffAssignment());
        rule.setPointsAwarded(request.pointsAwarded());
        TransactionRule saved = transactionRuleRepository.saveAndFlush(rule);
        // DB triggers may normalize duplicate settings on write; re-read so the response shows persisted values.
        if (entityManager.contains(saved)) {
            entityManager.refresh(saved);
        }
        return toTransactionRule(saved);
    }

    private int normalizeNonNegative(int value, int fallback) {
        return value < 0 ? fallback : value;
    }

    private Event requireOrganizerEvent(UUID organizerUserId, UUID eventId, AccountRole role) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        UserProfile user = userProfileRepository.findById(organizerUserId)
                .orElseThrow(() -> new ForbiddenException("Organizer account not found"));
        boolean owner = organizerUserId.equals(event.getOrganizerUserId());
        if (!owner && !AccountRoles.isAtLeast(role, AccountRole.ADMIN)) {
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
        String organizerName = event.getOrganizerUserId() == null ? "Organizer"
                : userProfileRepository.findById(event.getOrganizerUserId()).map(UserProfile::getFullName)
                        .filter(name -> name != null && !name.isBlank()).orElse("Organizer");
        // Unlimited capacity (0) is reported as -1 available slots; otherwise remaining seats.
        int availableSlots = capacity <= 0 ? -1 : Math.max(0, capacity - currentAttendeeCount);
        long attendedDistinct = transactions.stream()
                .filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                        && tx.getTransactionType() == TransactionType.ATTENDANCE)
                .map(TransactionLog::getAttendeeUserId).distinct().count();
        return new OrganizerEventResponse(eventId, event.getTitle(), organizerName, formatRange(event.getEventStartAt(), event.getEventEndAt()),
                format(event.getEventStartAt()), event.getLocation(), displayStatus(event.getStatus()),
                format(event.getCreatedAt()), event.getRejectionReason(), event.getDescription(),
                event.getEventStartAt(), event.getEventEndAt(), event.getRegistrationOpenAt(), event.getRegistrationCloseAt(),
                capacity, currentAttendeeCount, availableSlots, List.of(), (long) currentAttendeeCount,
                registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.ENTERED
                        || reg.getStatus() == RegistrationStatus.EXITED).count(),
                attendedDistinct,
                registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.EXITED).count(),
                registrations.stream().filter(reg -> reg.getStatus() == RegistrationStatus.NO_SHOW).count(),
                transactions.size(),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED).count(),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.REJECTED).count(),
                countApproved(transactions, TransactionType.BENEFIT_CLAIM),
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                        && (tx.getTransactionType() == TransactionType.BOOTH_VISIT || tx.getTransactionType() == TransactionType.SESSION_VISIT)).count(),
                redemptions,
                // Points handed out only: redemptions are negative deltas and must not net the total down.
                transactions.stream().filter(tx -> tx.getTransactionResult() == TransactionResult.APPROVED
                                && tx.getPointsDelta() > 0)
                        .mapToLong(TransactionLog::getPointsDelta).sum(),
                idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId).isPresent() ? "Configured" : "Not configured",
                event.isRewardsEnabled() ? "Enabled" : "Disabled",
                staffAssignmentRepository.findByEventId(eventId).size(),
                scanPurposeRepository.findByEventId(eventId).stream().filter(ScanPurpose::isActive).count(),
                event.isRewardsEnabled(), event.getOrganizerUserId(), event.getEventLogoUrl());
    }

    /**
     * Points earned per attendee for one event: sum of POSITIVE point_transactions (same rule as
     * RegistrationService, so organizer and staff screens agree). One batched query.
     */
    private Map<UUID, Integer> earnedPoints(UUID eventId, List<EventRegistration> registrations) {
        if (registrations.isEmpty()) {
            return Map.of();
        }
        List<UUID> attendeeIds = registrations.stream().map(EventRegistration::getAttendeeUserId).distinct().toList();
        Map<UUID, Integer> totals = new java.util.HashMap<>();
        for (PointTransactionRepository.EarnedPointsRow row : pointTransactionRepository.sumEarnedPoints(List.of(eventId), attendeeIds)) {
            if (eventId.equals(row.getEventId())) {
                long total = row.getTotal() == null ? 0 : row.getTotal();
                totals.put(row.getAttendeeUserId(), (int) Math.min(total, Integer.MAX_VALUE));
            }
        }
        return totals;
    }

    private OrganizerAttendeeResponse toAttendee(EventRegistration registration, List<TransactionLog> logs, int pointsEarned) {
        List<TransactionLog> attendeeLogs = logs.stream()
                .filter(log -> log.getRegistrationId().equals(registration.getId()))
                .sorted(Comparator.comparing(TransactionLog::getScannedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new OrganizerAttendeeResponse(registration.getAttendeeUserId(), registration.getId(), registration.getEventId(),
                registration.getQrCredentialId(), registration.getAttendeeName(), registration.getAttendeeEmail(),
                null, registration.getStatus().name(), eventStatus(registration),
                pointsEarned,
                attendeeLogs.stream().filter(log -> log.getTransactionResult() == TransactionResult.APPROVED)
                        .map(TransactionLog::getScannedAt).filter(java.util.Objects::nonNull)
                        .max(Instant::compareTo).map(this::format).orElse("-"),
                format(registration.getRegisteredAt()),
                registration.getQrCredentialId() == null ? "Pending" : "Issued",
                attendeeLogs.stream().filter(log -> log.getTransactionResult() == TransactionResult.APPROVED)
                        .map(log -> new TransactionEntry(log.getTransactionType().name(), format(log.getScannedAt())))
                        .limit(5).toList(),
                attendeeLogs.stream().filter(log -> log.getTransactionResult() == TransactionResult.REJECTED)
                        .map(TransactionLog::getReason).limit(5).toList(),
                registration.getStatus().isCountedAsRegistered());
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
                assignment.getAddedAt(), false);
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

    /**
     * Exact permission tokens (case-insensitive) the mobile app and defaults use. Anything else is a 400.
     * "View attendee details" is informational and maps to no flag.
     */
    private static final java.util.Set<String> ALLOWED_PERMISSION_TOKENS = java.util.Set.of(
            "scan qr", "print id", "view logs", "manage rewards", "view attendee details");

    private void validatePermissionTokens(List<String> permissions) {
        if (permissions == null) {
            return;
        }
        for (String token : permissions) {
            if (token == null || !ALLOWED_PERMISSION_TOKENS.contains(token.trim().toLowerCase(java.util.Locale.ROOT))) {
                throw new BadRequestException("Unknown permission: " + token);
            }
        }
    }

    /**
     * Maps a (validated) permissions list to the flags. Print/logs/rewards are set by presence; canScan
     * is only ever turned ON by a "Scan QR" token and is never cleared implicitly by a list.
     */
    private void applyPermissionOverrides(EventStaffAssignment assignment, List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        validatePermissionTokens(permissions);
        java.util.Set<String> tokens = permissions.stream()
                .map(token -> token.trim().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (tokens.contains("scan qr")) {
            assignment.setCanScan(true);
        }
        assignment.setCanPrintId(tokens.contains("print id"));
        assignment.setCanViewLogs(tokens.contains("view logs"));
        assignment.setCanManageRewards(tokens.contains("manage rewards"));
    }

    private String eventStatus(EventRegistration registration) {
        // Derived purely from the registration status (attendedAt is ignored: an EXITED attendee
        // also has attendedAt set but must not read as checked in).
        return switch (registration.getStatus()) {
            case ENTERED -> "Checked In";
            case EXITED -> "Exited";
            case NO_SHOW -> "No Show";
            case CANCELLED -> "Cancelled";
            case REGISTERED -> "Registered";
        };
    }

    private String displayStatus(EventStatus status) {
        return switch (status) {
            case APPROVED -> "Approved";
            // ACTIVE is shown as its own lifecycle label so the client can distinguish an
            // Upcoming (APPROVED) event from one that is ongoing and therefore edit-locked.
            case ACTIVE -> "Active";
            case ENDED -> "Completed";
            case REJECTED -> "Rejected";
            case CANCELLED -> "Cancelled";
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

