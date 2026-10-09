package com.thedavelopers.eventqr.shared.interfaces;

import java.util.UUID;

public interface RegistrationCommandPort {

    /**
     * Row-locks the registration (SELECT ... FOR UPDATE) for the rest of the caller's transaction, so
     * read-then-write scan checks for one registration run one at a time, and returns its state as read under
     * the lock. Callers must re-check status against this snapshot, not one read before the lock. Must be called
     * inside a transaction.
     */
    RegistrationLookupPort.RegistrationSnapshot lockForUpdate(UUID registrationId);

    void markEntered(UUID registrationId);

    void markExited(UUID registrationId);

    void markAttended(UUID registrationId);

    void setQrCredentialId(UUID registrationId, UUID qrCredentialId);

    void addPoints(UUID registrationId, int points);
}