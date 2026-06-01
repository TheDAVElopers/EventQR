package com.thedavelopers.eventqr.features.transactions.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;

public record TransactionResponse(UUID transactionId,
                                  UUID eventId,
                                  String eventTitle,
                                  UUID attendeeUserId,
                                  String attendeeName,
                                  UUID registrationId,
                                  String registrationStatus,
                                  UUID qrCredentialId,
                                  UUID scanPurposeId,
                                  String scanPurposeName,
                                  TransactionType transactionType,
                                  TransactionResult transactionResult,
                                  int pointsDelta,
                                  String reason,
                                  Instant scannedAt) {
}
