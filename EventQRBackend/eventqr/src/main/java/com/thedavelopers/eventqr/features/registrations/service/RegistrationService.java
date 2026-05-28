package com.thedavelopers.eventqr.features.registrations.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exception.ConflictException;
import com.thedavelopers.eventqr.shared.exception.ForbiddenException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.port.EventLookupPort;
import com.thedavelopers.eventqr.shared.port.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.port.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.port.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.port.RegistrationLookupPort.RegistrationSnapshot;

@Service
@Transactional
public class RegistrationService implements RegistrationLookupPort, RegistrationCommandPort {

    private final EventRegistrationRepository registrationRepository;
    private final AttendeeDirectoryPort attendeeDirectoryPort;
    private final EventLookupPort eventLookupPort;
    private final QrCredentialPort qrCredentialPort;
    private final EventService eventService;

    public RegistrationService(EventRegistrationRepository registrationRepository,
                               AttendeeDirectoryPort attendeeDirectoryPort,
                               EventLookupPort eventLookupPort,
                               QrCredentialPort qrCredentialPort,
                               EventService eventService) {
        this.registrationRepository = registrationRepository;
        this.attendeeDirectoryPort = attendeeDirectoryPort;
        this.eventLookupPort = eventLookupPort;
        this.qrCredentialPort = qrCredentialPort;
        this.eventService = eventService;
    }

    public RegistrationResponse register(RegistrationRequest request) {
        EventLookupPort.EventSnapshot eventSnapshot = eventLookupPort.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventId()));
        if (!eventSnapshot.registrationOpen()) {
            throw new ForbiddenException("Registration is closed or event is not approved");
        }
        if (eventSnapshot.isFull()) {
            throw new ConflictException("Event is at capacity");
        }
        if (registrationRepository.existsByEventIdAndAttendeeUserId(request.eventId(), attendeeSnapshot.userId())) {
            throw new ConflictException("Duplicate registration for this event and email");
        }

        AttendeeDirectoryPort.AttendeeSnapshot attendeeSnapshot = attendeeDirectoryPort.findOrCreateAttendee(
                request.email(), request.fullName(), request.phoneNumber(), AccountRole.ATTENDEE);

        EventRegistration registration = new EventRegistration();
        registration.setEventId(request.eventId());
        registration.setAttendeeUserId(attendeeSnapshot.userId());
        registration.setAttendeeEmail(attendeeSnapshot.email());
        registration.setAttendeeName(attendeeSnapshot.fullName());
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(Instant.now());
        registration = registrationRepository.save(registration);

        eventService.incrementCurrentAttendeeCount(request.eventId());

        QrCredentialPort.QrCredentialSnapshot qrCredential = qrCredentialPort.issueOrReturnExisting(request.eventId(),
                attendeeSnapshot.userId(), registration.getId(), attendeeSnapshot.email());
        qrCredentialPort.markEmailQueued(qrCredential.qrCredentialId());

        registration.setQrCredentialId(qrCredential.qrCredentialId());
        registration = registrationRepository.save(registration);

        return toResponse(registration);
    }

    public List<RegistrationResponse> findByEvent(UUID eventId) {
        return registrationRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public List<RegistrationResponse> findByAttendeeUserId(UUID attendeeUserId) {
        return registrationRepository.findByAttendeeUserId(attendeeUserId).stream().map(this::toResponse).toList();
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
        return getOrCreateQrCredential(registrationId);
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
        return new RegistrationResponse(registration.getId(), registration.getEventId(), registration.getAttendeeUserId(),
                registration.getAttendeeEmail(), registration.getAttendeeName(), registration.getStatus(),
            registration.getQrCredentialId(), registration.getRegisteredAt(), eventSnapshot.title(),
            eventSnapshot.location(), eventSnapshot.eventStartAt(), eventSnapshot.eventEndAt());
    }

    private RegistrationSnapshot toSnapshot(EventRegistration registration) {
        return new RegistrationSnapshot(registration.getId(), registration.getEventId(), registration.getAttendeeUserId(),
                registration.getAttendeeEmail(), registration.getAttendeeName(), registration.getStatus(),
                registration.getQrCredentialId(), registration.getRegisteredAt(), registration.getEnteredAt(),
                registration.getExitedAt(), registration.getAttendedAt(), registration.getPointsEarned());
    }
}