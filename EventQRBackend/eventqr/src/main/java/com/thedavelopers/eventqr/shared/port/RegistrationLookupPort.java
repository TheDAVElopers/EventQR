package com.thedavelopers.eventqr.shared.port;

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

    List<RegistrationSnapshot> listByEventId(UUID eventId);

    record RegistrationSnapshot(UUID registrationId, UUID eventId, UUID attendeeUserId, String attendeeEmail,
                                String attendeeName, RegistrationStatus status, UUID qrCredentialId,
                                Instant registeredAt, Instant enteredAt, Instant exitedAt, Instant attendedAt,
                                Integer pointsEarned) {
    }
}