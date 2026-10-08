package com.thedavelopers.eventqr.shared.interfaces;

import java.util.Optional;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;

public interface QrCredentialPort {

    QrCredentialSnapshot issueCredential(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail);

    QrCredentialSnapshot issueOrReturnExisting(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail);

    /**
     * Replaces the registration's credential with a fresh, active one (new QR value, delivery state reset); issues
     * one if none exists. Used when a cancelled registration is reactivated so the old pass stays dead.
     */
    QrCredentialSnapshot reissueCredential(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail);

    Optional<QrCredentialSnapshot> findById(UUID qrCredentialId);

    Optional<QrCredentialSnapshot> findByRegistrationId(UUID registrationId);

    Optional<QrCredentialSnapshot> findByQrValue(String qrValue);

    byte[] renderQrImage(String qrValue);

    QrCredentialSnapshot markDisplayedOnce(UUID qrCredentialId);

    QrCredentialSnapshot markDownloaded(UUID qrCredentialId);

    QrCredentialSnapshot deactivate(UUID qrCredentialId);

    QrCredentialSnapshot markEmailQueued(UUID qrCredentialId);

    QrCredentialSnapshot markEmailSent(UUID qrCredentialId);

    QrCredentialSnapshot markEmailFailed(UUID qrCredentialId);

    record QrCredentialSnapshot(UUID qrCredentialId, UUID eventId, UUID attendeeUserId, UUID registrationId,
                                String qrValue, boolean active, QrDisplayStatus displayStatus,
                                QrDeliveryStatus deliveryStatus, boolean downloaded) {
    }
}