package com.thedavelopers.eventqr.features.transactions.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;

public record ScanVerificationResponse(UUID eventId, UUID attendeeUserId, UUID registrationId, UUID qrCredentialId,
                                       String qrValue, String attendeeName, String attendeeEmail,
                                       RegistrationStatus registrationStatus, UUID scanPurposeId,
                                       ScanPurposeCode scanPurposeCode, boolean qrActive, String message,
                                       Instant verifiedAt, boolean eligible) {
}