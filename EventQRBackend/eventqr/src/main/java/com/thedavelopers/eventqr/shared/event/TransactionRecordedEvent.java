package com.thedavelopers.eventqr.shared.event;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;

public record TransactionRecordedEvent(
        UUID transactionId,
        UUID eventId,
        UUID attendeeUserId,
        UUID registrationId,
        UUID qrCredentialId,
        UUID scanPurposeId,
        TransactionType transactionType,
        TransactionResult transactionResult,
        int pointsDelta,
        UUID staffUserId,
        String reason) {
}