package com.thedavelopers.eventqr.features.transactions.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;

public record TransactionResponse(UUID transactionId, UUID eventId, UUID attendeeUserId, UUID registrationId,
                                  UUID qrCredentialId, UUID scanPurposeId, TransactionType transactionType,
                                  TransactionResult transactionResult, int pointsDelta, String reason,
                                  Instant scannedAt, String eventTitle) {
}