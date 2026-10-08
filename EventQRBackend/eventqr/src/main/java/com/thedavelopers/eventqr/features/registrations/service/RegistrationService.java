package com.thedavelopers.eventqr.features.registrations.service;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.exceptions.TooManyRequestsException;
import com.thedavelopers.eventqr.shared.security.RegistrationRateLimiter;
import com.thedavelopers.eventqr.shared.utils.LikePatterns;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationEmailRequestedEvent;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot;

@Service
@Transactional
public class RegistrationService implements RegistrationLookupPort, RegistrationCommandPort {

    private static final String REGISTER_OWN_EMAIL_MESSAGE = "You can only register using your own account email";
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final EventRegistrationRepository registrationRepository;
    private final AttendeeDirectoryPort attendeeDirectoryPort;
    private final NotificationService notificationService;
    private final EventStaffAssignmentRepository staffAssignmentRepository;
    private final EventLookupPort eventLookupPort;
    private final QrCredentialPort qrCredentialPort;
    private final EventService eventService;
    private final QREmailService qrEmailService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RegistrationRateLimiter registrationRateLimiter;
    private final PointTransactionRepository pointTransactionRepository;

    public RegistrationService(EventRegistrationRepository registrationRepository,
                               AttendeeDirectoryPort attendeeDirectoryPort,
                               NotificationService notificationService,
                               EventStaffAssignmentRepository staffAssignmentRepository,
                               EventLookupPort eventLookupPort,
                               QrCredentialPort qrCredentialPort,
                               EventService eventService,
                               QREmailService qrEmailService,
                               ApplicationEventPublisher applicationEventPublisher,
                               RegistrationRateLimiter registrationRateLimiter,
                               PointTransactionRepository pointTransactionRepository) {
        this.registrationRepository = registrationRepository;
        this.attendeeDirectoryPort = attendeeDirectoryPort;
        this.notificationService = notificationService;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.eventLookupPort = eventLookupPort;
        this.qrCredentialPort = qrCredentialPort;
        this.eventService = eventService;
        this.qrEmailService = qrEmailService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.registrationRateLimiter = registrationRateLimiter;
        this.pointTransactionRepository = pointTransactionRepository;
    }

    /**
     * Registers for an event on behalf of an authenticated caller. The registration email must be
     * the caller's own profile email; only ADMIN/SUPER_ADMIN may register another email. The check
     * runs before the rate limiter and before any event or attendee lookup, so a mismatch reveals
     * nothing about whether the email exists or is already registered, creates no profile, and
     * consumes no rate-limit budget. The limiter is then keyed on the client IP and the caller's
     * user id (never the body email), so nobody can exhaust another user's budget. Both registration
     * endpoints call this method, so they cannot diverge.
     */
    @CacheEvict(cacheNames = {"events", "registrations"}, allEntries = true)
    public RegistrationSubmissionResponse registerAs(RegistrationRequest request, UUID callerUserId,
                                                     AccountRole callerRole, String clientIp) {
        requireOwnEmailOrAdmin(request.email(), callerUserId, callerRole);
        if (!registrationRateLimiter.allow(clientIp, callerUserId)) {
            throw new TooManyRequestsException("Too many registration requests. Please try again later.");
        }
        return register(request);
    }

    private void requireOwnEmailOrAdmin(String requestedEmail, UUID callerUserId, AccountRole callerRole) {
        if (callerRole == AccountRole.ADMIN || callerRole == AccountRole.SUPER_ADMIN) {
            return;
        }
        String callerEmail = callerUserId == null ? null
                : attendeeDirectoryPort.findById(callerUserId).map(AttendeeDirectoryPort.AttendeeSnapshot::email).orElse(null);
        if (callerEmail == null || requestedEmail == null || !requestedEmail.trim().equalsIgnoreCase(callerEmail.trim())) {
            throw new ForbiddenException(REGISTER_OWN_EMAIL_MESSAGE);
        }
    }

    /** Unchecked core; callers outside this package must go through {@link #registerAs}. */
    RegistrationSubmissionResponse register(RegistrationRequest request) {
        EventLookupPort.EventSnapshot eventSnapshot = eventLookupPort.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));
        Instant now = Instant.now();
        boolean registrationWindowOpen = (eventSnapshot.registrationOpenAt() == null || !now.isBefore(eventSnapshot.registrationOpenAt()))
                && (eventSnapshot.registrationCloseAt() == null || !now.isAfter(eventSnapshot.registrationCloseAt()));
        boolean statusPublic = eventSnapshot.status() == EventStatus.APPROVED || eventSnapshot.status() == EventStatus.ACTIVE;
        if (!statusPublic) {
            throw new ForbiddenException("Event is not open for registration");
        }
        if (!registrationWindowOpen) {
            throw new ForbiddenException("Registration is closed");
        }
        if (eventSnapshot.isFull()) {
            throw new ConflictException("Event is at capacity");
        }
        String normalizedEmail = request.email().trim();
        EventRegistration cancelledByEmail = null;
        var byEmail = registrationRepository.findByEventIdAndAttendeeEmailIgnoreCase(request.eventId(), normalizedEmail);
        if (byEmail.isPresent()) {
            if (byEmail.get().getStatus() != RegistrationStatus.CANCELLED) {
                rejectDuplicate(byEmail.get());
            }
            cancelledByEmail = byEmail.get();
        }

        AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot = attendeeDirectoryPort.findOrCreateAttendee(
                normalizedEmail, request.fullName(), request.phoneNumber(), AccountRole.ATTENDEE);

        if (eventSnapshot.organizerUserId() != null && eventSnapshot.organizerUserId().equals(attendeeSnapshot.userId())) {
            throw new ForbiddenException("You cannot register for your own event");
        }

        EventRegistration cancelled = cancelledByEmail;
        var byUser = registrationRepository.findFirstByEventIdAndAttendeeUserId(request.eventId(), attendeeSnapshot.userId());
        if (byUser.isPresent()) {
            if (byUser.get().getStatus() != RegistrationStatus.CANCELLED) {
                rejectDuplicate(byUser.get());
            }
            if (cancelled == null) {
                cancelled = byUser.get();
            }
        }
        if (cancelled != null) {
            return reactivate(cancelled, eventSnapshot, attendeeSnapshot);
        }

        EventRegistration registration = new EventRegistration();
        registration.setEventId(request.eventId());
        registration.setAttendeeUserId(attendeeSnapshot.userId());
        registration.setAttendeeEmail(attendeeSnapshot.email());
        registration.setAttendeeName(attendeeSnapshot.fullName());
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(Instant.now());
        registration = registrationRepository.saveAndFlush(registration);
        log.info("Registration saved registrationId={} eventId={} attendeeUserId={}",
            registration.getId(), registration.getEventId(), registration.getAttendeeUserId());

        eventService.incrementCurrentAttendeeCount(request.eventId());

        return finishRegistration(registration.getId(), eventSnapshot, attendeeSnapshot, false);
    }

    /**
     * Restores a CANCELLED registration. The seat is claimed first with the capacity-guarded counter increment (a
     * full event throws and leaves the row CANCELLED, the counter untouched); the guarded status flip then decides
     * which concurrent caller wins, and the loser returns the seat. Same window/status/organizer rules as a new
     * registration have already been applied by the caller.
     */
    private RegistrationSubmissionResponse reactivate(EventRegistration cancelled, EventSnapshot eventSnapshot,
                                                      AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot) {
        UUID registrationId = cancelled.getId();
        eventService.incrementCurrentAttendeeCount(cancelled.getEventId());
        int updated = registrationRepository.updateStatusIfCurrent(registrationId,
                RegistrationStatus.CANCELLED.name(), RegistrationStatus.REGISTERED.name());
        if (updated == 0) {
            eventService.decrementCurrentAttendeeCount(cancelled.getEventId());
            throw new ConflictException("Duplicate registration for this event and attendee");
        }
        entityManager.flush();
        entityManager.clear();
        EventRegistration restored = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        restored.setRegisteredAt(Instant.now());
        restored.setEnteredAt(null);
        restored.setExitedAt(null);
        restored.setAttendedAt(null);
        registrationRepository.saveAndFlush(restored);
        log.info("Registration reactivated registrationId={} eventId={} attendeeUserId={}",
                registrationId, restored.getEventId(), restored.getAttendeeUserId());
        return finishRegistration(registrationId, eventSnapshot, attendeeSnapshot, true);
    }

    private RegistrationSubmissionResponse finishRegistration(UUID registrationId, EventSnapshot eventSnapshot,
                                                              AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot,
                                                              boolean reissueQr) {
        entityManager.flush();
        entityManager.clear();

        EventRegistration savedRegistration = registrationRepository.findById(registrationId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));

        notifyOrganizerOnRegistration(eventSnapshot, attendeeSnapshot.fullName());

        log.info("Generating or recovering QR credential registrationId={}", registrationId);
        QrCredentialSnapshot qrCredential = reissueQr
            ? qrCredentialPort.reissueCredential(savedRegistration.getEventId(), savedRegistration.getAttendeeUserId(),
                savedRegistration.getId(), savedRegistration.getAttendeeEmail())
            : qrCredentialPort.issueOrReturnExisting(savedRegistration.getEventId(), savedRegistration.getAttendeeUserId(),
                savedRegistration.getId(), savedRegistration.getAttendeeEmail());
        if (!qrCredential.qrCredentialId().equals(savedRegistration.getQrCredentialId())) {
            savedRegistration.setQrCredentialId(qrCredential.qrCredentialId());
            savedRegistration = registrationRepository.saveAndFlush(savedRegistration);
            log.info("QR credential linked registrationId={} qrCredentialId={}",
                registrationId, qrCredential.qrCredentialId());
        } else {
            log.info("QR credential already linked registrationId={} qrCredentialId={}",
                registrationId, qrCredential.qrCredentialId());
        }

        // Confirmation is only created once the QR pass actually exists.
        notificationService.createRegistrationConfirmationNotification(
                eventSnapshot.eventId(), attendeeSnapshot.userId(), eventSnapshot.title());

        log.info("Starting QR email delivery registrationId={} qrCredentialId={}",
            registrationId, qrCredential.qrCredentialId());
        // Publish after-commit — the email (gateway call + retry sleeps) must NOT run
        // inside this transaction holding a JDBC connection. RegistrationEmailListener
        // dispatches it onto the bounded async executor once the commit completes.
        applicationEventPublisher.publishEvent(new RegistrationEmailRequestedEvent(registrationId));
        log.info("Registration workflow completed registrationId={} qrCredentialId={}",
            registrationId, qrCredential.qrCredentialId());

        // A registration that was just created cannot have earned points yet.
        return new RegistrationSubmissionResponse(toResponse(savedRegistration, 0), qrCredential);
    }

    private void notifyOrganizerOnRegistration(EventSnapshot eventSnapshot, String attendeeName) {
        if (eventSnapshot.organizerUserId() == null) {
            return;
        }
        notificationService.createNewRegistrationNotification(
                eventSnapshot.eventId(), eventSnapshot.organizerUserId(), eventSnapshot.title(), attendeeName);
        int capacity = eventSnapshot.capacity();
        if (capacity > 0) {
            // Post-increment counter on the event row (excludes cancelled registrations).
            long currentCount = eventLookupPort.findById(eventSnapshot.eventId())
                    .map(EventSnapshot::currentAttendeeCount).orElse(eventSnapshot.currentAttendeeCount() + 1);
            if (currentCount >= capacity) {
                notificationService.createCapacityFullNotification(
                        eventSnapshot.eventId(), eventSnapshot.organizerUserId(), eventSnapshot.title(),
                        (int) currentCount, capacity);
                notifyAssignedStaffCapacityFull(eventSnapshot.eventId(), eventSnapshot.organizerUserId(),
                        eventSnapshot.title(), (int) currentCount, capacity);
            } else if (currentCount >= capacity * 0.8) {
                notificationService.createCapacityWarningNotification(
                        eventSnapshot.eventId(), eventSnapshot.organizerUserId(), eventSnapshot.title(),
                        (int) currentCount, capacity);
            }
        }
    }

    private void rejectDuplicate(EventRegistration existing) {
        throw new ConflictException("Duplicate registration for this event and attendee");
    }

    private void notifyAssignedStaffCapacityFull(UUID eventId, UUID organizerUserId, String eventTitle, int count, int capacity) {
        staffAssignmentRepository.findByEventIdAndActiveTrue(eventId).stream()
            .map(EventStaffAssignment::getStaffUserId)
            .filter(staffUserId -> !staffUserId.equals(organizerUserId))
            .forEach(staffUserId -> notificationService.createCapacityFullNotification(
                    eventId, staffUserId, eventTitle, count, capacity));
    }

    public List<RegistrationResponse> findByEvent(UUID eventId) {
        return toResponses(registrationRepository.findByEventId(eventId));
    }

    public Page<RegistrationResponse> findByEvent(UUID eventId, Pageable pageable) {
        return toResponsePage(registrationRepository.findByEventId(eventId, pageable));
    }

    /**
     * Server-side search over an event's registrations. {@code q} is a case-insensitive contains match on
     * attendee name, email or registration number; {@code status} optionally narrows by status. With
     * neither filter this is the plain paged listing.
     */
    public Page<RegistrationResponse> findByEvent(UUID eventId, String q, RegistrationStatus status, Pageable pageable) {
        String pattern = LikePatterns.contains(q == null ? null : q.trim().replaceFirst("^#", ""));
        if (pattern == null && status == null) {
            return findByEvent(eventId, pageable);
        }
        Set<RegistrationStatus> statuses = status == null ? EnumSet.allOf(RegistrationStatus.class) : EnumSet.of(status);
        return toResponsePage(registrationRepository.searchByEvent(eventId, pattern == null ? "%" : pattern, statuses, pageable));
    }

    public List<RegistrationResponse> findByAttendeeUserId(UUID attendeeUserId) {
        return toResponses(registrationRepository.findByAttendeeUserId(attendeeUserId));
    }

    public Page<RegistrationResponse> findByAttendeeUserId(UUID attendeeUserId, Pageable pageable) {
        return toResponsePage(registrationRepository.findByAttendeeUserId(attendeeUserId, pageable));
    }

    public RegistrationResponse findOne(UUID registrationId) {
        return toResponse(registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId)));
    }

    public RegistrationResponse findOneForAttendee(UUID registrationId, UUID attendeeUserId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        if (!registration.getAttendeeUserId().equals(attendeeUserId)) {
            throw new ForbiddenException("You can only view your own registration");
        }
        return toResponse(registration);
    }

    @CacheEvict(cacheNames = {"events", "registrations"}, allEntries = true)
    public RegistrationResponse cancel(UUID registrationId, UUID attendeeUserId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        if (!registration.getAttendeeUserId().equals(attendeeUserId)) {
            throw new ForbiddenException("You can only cancel your own registration");
        }
        RegistrationStatus current = registration.getStatus();
        if (current == RegistrationStatus.ENTERED || current == RegistrationStatus.EXITED
                || current == RegistrationStatus.NO_SHOW) {
            throw new ConflictException("This registration can no longer be cancelled because the attendee has already "
                    + (current == RegistrationStatus.NO_SHOW ? "been marked as a no-show" : "checked in"));
        }
        if (current == RegistrationStatus.REGISTERED) {
            // Guarded transition: only the caller that wins the update decrements the counter.
            int updated = registrationRepository.updateStatusIfCurrent(registrationId,
                    RegistrationStatus.REGISTERED.name(), RegistrationStatus.CANCELLED.name());
            if (updated == 1) {
                eventService.decrementCurrentAttendeeCount(registration.getEventId());
            }
            entityManager.flush();
            entityManager.clear();
            registration = registrationRepository.findById(registrationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        }
        if (registration.getQrCredentialId() != null) {
            qrCredentialPort.findById(registration.getQrCredentialId()).ifPresent(qr -> qrCredentialPort.deactivate(qr.qrCredentialId()));
        }
        return toResponse(registration);
    }

    public QrCredentialSnapshot getOrCreateQrCredential(UUID registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        if (registration.getQrCredentialId() != null) {
            return qrCredentialPort.findById(registration.getQrCredentialId())
                    .orElseThrow(() -> new ResourceNotFoundException("QR credential not found for registration: " + registrationId));
        }
        QrCredentialSnapshot qrCredential = qrCredentialPort.issueCredential(registration.getEventId(), registration.getAttendeeUserId(),
                registration.getId(), registration.getAttendeeEmail());
        registration.setQrCredentialId(qrCredential.qrCredentialId());
        registrationRepository.save(registration);
        return qrCredential;
    }

    public QrCredentialSnapshot linkQrCredential(UUID registrationId) {
        QrCredentialSnapshot qrCredential = getOrCreateQrCredential(registrationId);
        qrEmailService.sendForRegistrationSafelyAsync(registrationId);
        return qrCredential;
    }

    @Override
    public RegistrationSnapshot requireById(UUID registrationId) {
        return registrationRepository.findById(registrationId).map(this::toSnapshot)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
    }

    @Override
    public java.util.Optional<RegistrationSnapshot> findById(UUID registrationId) {
        return registrationRepository.findById(registrationId).map(this::toSnapshot);
    }

    @Override
    public java.util.Optional<RegistrationSnapshot> findByQrCredentialId(UUID qrCredentialId) {
        return registrationRepository.findByQrCredentialId(qrCredentialId).map(this::toSnapshot);
    }

    @Override
    public java.util.Optional<RegistrationSnapshot> findByEventIdAndAttendeeEmail(UUID eventId, String attendeeEmail) {
        return registrationRepository.findByEventIdAndAttendeeEmailIgnoreCase(eventId, attendeeEmail).map(this::toSnapshot);
    }

    @Override
    public java.util.Optional<RegistrationSnapshot> findByEventIdAndRegistrationNumber(UUID eventId, Integer registrationNumber) {
        return registrationRepository.findByEventIdAndRegistrationNumber(eventId, registrationNumber).map(this::toSnapshot);
    }

    @Override
    public List<RegistrationSnapshot> listByEventId(UUID eventId) {
        return toSnapshots(registrationRepository.findByEventId(eventId));
    }

    @Override
    public void markEntered(UUID registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        registration.setStatus(RegistrationStatus.ENTERED);
        registration.setEnteredAt(Instant.now());
        registrationRepository.save(registration);
    }

    @Override
    public void markExited(UUID registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        registration.setStatus(RegistrationStatus.EXITED);
        registration.setExitedAt(Instant.now());
        registrationRepository.save(registration);
    }

    @Override
    public void markAttended(UUID registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        registration.setAttendedAt(Instant.now());
        registrationRepository.save(registration);
    }

    @Override
    public void setQrCredentialId(UUID registrationId, UUID qrCredentialId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        registration.setQrCredentialId(qrCredentialId);
        registrationRepository.save(registration);
    }

    @Override
    public void addPoints(UUID registrationId, int points) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        registration.setPointsEarned((registration.getPointsEarned() == null ? 0 : registration.getPointsEarned()) + points);
        registrationRepository.save(registration);
    }

    private record PointsKey(UUID eventId, UUID attendeeUserId) {
    }

    /**
     * Points earned = sum of POSITIVE point_transactions for (event, attendee); deductions are ignored.
     * One batched query for the whole set of registrations.
     */
    private Map<PointsKey, Integer> earnedPoints(Collection<EventRegistration> registrations) {
        if (registrations.isEmpty()) {
            return Map.of();
        }
        Set<UUID> eventIds = new HashSet<>();
        Set<UUID> attendeeIds = new HashSet<>();
        for (EventRegistration registration : registrations) {
            eventIds.add(registration.getEventId());
            attendeeIds.add(registration.getAttendeeUserId());
        }
        Map<PointsKey, Integer> totals = new HashMap<>();
        for (PointTransactionRepository.EarnedPointsRow row : pointTransactionRepository.sumEarnedPoints(eventIds, attendeeIds)) {
            long total = row.getTotal() == null ? 0 : row.getTotal();
            totals.put(new PointsKey(row.getEventId(), row.getAttendeeUserId()), (int) Math.min(total, Integer.MAX_VALUE));
        }
        return totals;
    }

    private List<RegistrationResponse> toResponses(List<EventRegistration> registrations) {
        Map<PointsKey, Integer> points = earnedPoints(registrations);
        return registrations.stream()
                .map(r -> toResponse(r, points.getOrDefault(new PointsKey(r.getEventId(), r.getAttendeeUserId()), 0)))
                .toList();
    }

    private Page<RegistrationResponse> toResponsePage(Page<EventRegistration> page) {
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private RegistrationResponse toResponse(EventRegistration registration) {
        return toResponse(registration, earnedPoints(List.of(registration))
                .getOrDefault(new PointsKey(registration.getEventId(), registration.getAttendeeUserId()), 0));
    }

    private RegistrationResponse toResponse(EventRegistration registration, int pointsEarned) {
        EventSnapshot eventSnapshot = eventLookupPort.findById(registration.getEventId())
            .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + registration.getEventId()));
        var attendeeSnapshot = attendeeDirectoryPort.findById(registration.getAttendeeUserId());
        String attendeePhoneNumber = attendeeSnapshot
                .map(AttendeeDirectoryPort.AttendeeSnapshot::phoneNumber)
                .orElse(null);
        String attendeeRole = attendeeSnapshot
                .map(s -> s.role() != null ? s.role().name() : null)
                .orElse(null);
        return new RegistrationResponse(registration.getId(), registration.getEventId(), registration.getAttendeeUserId(),
                registration.getAttendeeEmail(), registration.getAttendeeName(), registration.getStatus(),
            registration.getQrCredentialId(), registration.getRegisteredAt(), eventSnapshot.title(),
            eventSnapshot.location(), eventSnapshot.eventStartAt(), eventSnapshot.eventEndAt(), attendeePhoneNumber,
            registration.getEnteredAt(), registration.getExitedAt(), registration.getAttendedAt(),
            pointsEarned, registration.getRegistrationNumber(), attendeeRole);
    }

    private List<RegistrationSnapshot> toSnapshots(List<EventRegistration> registrations) {
        Map<PointsKey, Integer> points = earnedPoints(registrations);
        return registrations.stream()
                .map(r -> toSnapshot(r, points.getOrDefault(new PointsKey(r.getEventId(), r.getAttendeeUserId()), 0)))
                .toList();
    }

    private RegistrationSnapshot toSnapshot(EventRegistration registration) {
        return toSnapshot(registration, earnedPoints(List.of(registration))
                .getOrDefault(new PointsKey(registration.getEventId(), registration.getAttendeeUserId()), 0));
    }

    private RegistrationSnapshot toSnapshot(EventRegistration registration, int pointsEarned) {
        return new RegistrationSnapshot(registration.getId(), registration.getEventId(), registration.getAttendeeUserId(),
                registration.getAttendeeEmail(), registration.getAttendeeName(), registration.getStatus(),
                registration.getQrCredentialId(), registration.getRegisteredAt(), registration.getEnteredAt(),
                registration.getExitedAt(), registration.getAttendedAt(), pointsEarned,
                registration.getRegistrationNumber());
    }
}
