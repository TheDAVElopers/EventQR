package com.thedavelopers.eventqr.features.registrations.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationSubmissionResponse;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
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

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final EventRegistrationRepository registrationRepository;
    private final AttendeeDirectoryPort attendeeDirectoryPort;
    private final NotificationService notificationService;
    private final EventLookupPort eventLookupPort;
    private final QrCredentialPort qrCredentialPort;
    private final EventService eventService;
    private final QREmailService qrEmailService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public RegistrationService(EventRegistrationRepository registrationRepository,
                               AttendeeDirectoryPort attendeeDirectoryPort,
                               NotificationService notificationService,
                               EventLookupPort eventLookupPort,
                               QrCredentialPort qrCredentialPort,
                               EventService eventService,
                               QREmailService qrEmailService,
                               ApplicationEventPublisher applicationEventPublisher) {
        this.registrationRepository = registrationRepository;
        this.attendeeDirectoryPort = attendeeDirectoryPort;
        this.notificationService = notificationService;
        this.eventLookupPort = eventLookupPort;
        this.qrCredentialPort = qrCredentialPort;
        this.eventService = eventService;
        this.qrEmailService = qrEmailService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @CacheEvict(cacheNames = {"events", "registrations"}, key = "#request.eventId()")
    public RegistrationSubmissionResponse register(RegistrationRequest request) {
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
        if (registrationRepository.existsByEventIdAndAttendeeEmailIgnoreCase(request.eventId(), normalizedEmail)) {
            throw new ConflictException("Duplicate registration for this event and attendee");
        }

        AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot = attendeeDirectoryPort.findOrCreateAttendee(
                normalizedEmail, request.fullName(), request.phoneNumber(), AccountRole.ATTENDEE);

        if (eventSnapshot.organizerUserId() != null && eventSnapshot.organizerUserId().equals(attendeeSnapshot.userId())) {
            throw new ForbiddenException("Organizers cannot register for their own event");
        }

        if (registrationRepository.existsByEventIdAndAttendeeUserId(request.eventId(), attendeeSnapshot.userId())) {
            throw new ConflictException("Duplicate registration for this event and attendee");
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

        entityManager.flush();
        entityManager.clear();

        UUID registrationId = registration.getId();
        EventRegistration savedRegistration = registrationRepository.findById(registrationId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));

        notifyOrganizerOnRegistration(eventSnapshot, attendeeSnapshot.fullName());

        log.info("Generating or recovering QR credential registrationId={}", registrationId);
        QrCredentialSnapshot qrCredential = qrCredentialPort.issueOrReturnExisting(
            savedRegistration.getEventId(), savedRegistration.getAttendeeUserId(),
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

        log.info("Starting QR email delivery registrationId={} qrCredentialId={}",
            registrationId, qrCredential.qrCredentialId());
        // Publish after-commit — the email (gateway call + retry sleeps) must NOT run
        // inside this transaction holding a JDBC connection. RegistrationEmailListener
        // dispatches it onto the bounded async executor once the commit completes.
        applicationEventPublisher.publishEvent(new RegistrationEmailRequestedEvent(registrationId));
        log.info("Registration workflow completed registrationId={} qrCredentialId={}",
            registrationId, qrCredential.qrCredentialId());

        return new RegistrationSubmissionResponse(toResponse(savedRegistration), qrCredential);
    }

    private void notifyOrganizerOnRegistration(EventSnapshot eventSnapshot, String attendeeName) {
        if (eventSnapshot.organizerUserId() == null) {
            return;
        }
        notificationService.createNewRegistrationNotification(
                eventSnapshot.eventId(), eventSnapshot.organizerUserId(), eventSnapshot.title(), attendeeName);
        int capacity = eventSnapshot.capacity();
        if (capacity > 0) {
            long currentCount = registrationRepository.countByEventId(eventSnapshot.eventId());
            if (currentCount >= capacity) {
                notificationService.createCapacityFullNotification(
                        eventSnapshot.eventId(), eventSnapshot.organizerUserId(), eventSnapshot.title(),
                        (int) currentCount, capacity);
            } else if (currentCount >= capacity * 0.8) {
                notificationService.createCapacityWarningNotification(
                        eventSnapshot.eventId(), eventSnapshot.organizerUserId(), eventSnapshot.title(),
                        (int) currentCount, capacity);
            }
        }
    }

    public List<RegistrationResponse> findByEvent(UUID eventId) {
        return registrationRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public Page<RegistrationResponse> findByEvent(UUID eventId, Pageable pageable) {
        return registrationRepository.findByEventId(eventId, pageable).map(this::toResponse);
    }

    public List<RegistrationResponse> findByAttendeeUserId(UUID attendeeUserId) {
        return registrationRepository.findByAttendeeUserId(attendeeUserId).stream().map(this::toResponse).toList();
    }

    public Page<RegistrationResponse> findByAttendeeUserId(UUID attendeeUserId, Pageable pageable) {
        return registrationRepository.findByAttendeeUserId(attendeeUserId, pageable).map(this::toResponse);
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

    @CacheEvict(cacheNames = {"events", "registrations"}, key = "#attendeeUserId")
    public RegistrationResponse cancel(UUID registrationId, UUID attendeeUserId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        if (!registration.getAttendeeUserId().equals(attendeeUserId)) {
            throw new ForbiddenException("You can only cancel your own registration");
        }
        if (registration.getStatus() != RegistrationStatus.CANCELLED) {
            registration.setStatus(RegistrationStatus.CANCELLED);
            registrationRepository.save(registration);
            eventService.decrementCurrentAttendeeCount(registration.getEventId());
        }
        if (registration.getQrCredentialId() != null) {
            qrCredentialPort.findById(registration.getQrCredentialId()).ifPresent(qr -> qrCredentialPort.markEmailQueued(qr.qrCredentialId()));
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
        return registrationRepository.findByEventId(eventId).stream().map(this::toSnapshot).toList();
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

    private RegistrationResponse toResponse(EventRegistration registration) {
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
            registration.getPointsEarned(), registration.getRegistrationNumber(), attendeeRole);
    }

    private RegistrationSnapshot toSnapshot(EventRegistration registration) {
        return new RegistrationSnapshot(registration.getId(), registration.getEventId(), registration.getAttendeeUserId(),
                registration.getAttendeeEmail(), registration.getAttendeeName(), registration.getStatus(),
                registration.getQrCredentialId(), registration.getRegisteredAt(), registration.getEnteredAt(),
                registration.getExitedAt(), registration.getAttendedAt(), registration.getPointsEarned(),
                registration.getRegistrationNumber());
    }
}
