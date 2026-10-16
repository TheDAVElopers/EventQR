package com.thedavelopers.eventqr.shared.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;

public interface RegistrationLookupPort {

    RegistrationSnapshot requireById(UUID registrationId);

    Optional<RegistrationSnapshot> findById(UUID registrationId);

    Optional<RegistrationSnapshot> findByQrCredentialId(UUID qrCredentialId);

    Optional<RegistrationSnapshot> findByEventIdAndAttendeeEmail(UUID eventId, String attendeeEmail);

    Optional<RegistrationSnapshot> findByEventIdAndRegistrationNumber(UUID eventId, Integer registrationNumber);

    List<RegistrationSnapshot> listByEventId(UUID eventId);

    /** True when the attendee holds a REGISTERED / ENTERED / EXITED registration for the event. */
    boolean hasActiveRegistration(UUID eventId, UUID attendeeUserId);

    record RegistrationSnapshot(UUID registrationId, UUID eventId, UUID attendeeUserId, String attendeeEmail,
                                String attendeeName, RegistrationStatus status, UUID qrCredentialId,
                                Instant registeredAt, Instant enteredAt, Instant exitedAt, Instant attendedAt,
                                Integer pointsEarned, Integer registrationNumber) {
    }
}