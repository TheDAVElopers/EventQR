package com.thedavelopers.eventqr.features.registrations.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    boolean existsByEventIdAndAttendeeEmailIgnoreCase(UUID eventId, String attendeeEmail);

    boolean existsByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId);

    Optional<EventRegistration> findByEventIdAndAttendeeEmailIgnoreCase(UUID eventId, String attendeeEmail);

    Optional<EventRegistration> findByQrCredentialId(UUID qrCredentialId);

    List<EventRegistration> findByEventId(UUID eventId);

    List<EventRegistration> findByAttendeeUserId(UUID attendeeUserId);
}
