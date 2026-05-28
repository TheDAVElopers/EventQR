package com.thedavelopers.eventqr.shared.port;

import java.util.Optional;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;

public interface QrCredentialPort {

    QrCredentialSnapshot issueCredential(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail);

    QrCredentialSnapshot issueOrReturnExisting(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail);

    Optional<QrCredentialSnapshot> findById(UUID qrCredentialId);

    Optional<QrCredentialSnapshot> findByRegistrationId(UUID registrationId);

    Optional<QrCredentialSnapshot> findByQrValue(String qrValue);

    QrCredentialSnapshot markDisplayedOnce(UUID qrCredentialId);

    QrCredentialSnapshot markDownloaded(UUID qrCredentialId);

    QrCredentialSnapshot markEmailQueued(UUID qrCredentialId);

    record QrCredentialSnapshot(UUID qrCredentialId, UUID eventId, UUID attendeeUserId, UUID registrationId,
                                String qrValue, boolean active, QrDisplayStatus displayStatus,
                                QrDeliveryStatus deliveryStatus, boolean downloaded) {
    }
}