package com.thedavelopers.eventqr.features.organizer.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TransactionRuleRequest(@NotNull UUID scanPurposeId, boolean active, boolean allowDuplicate,
                                     int duplicateWindowMinutes, int maxUsesPerRegistration,
                                     boolean requiresStaffAssignment, @Min(0) int pointsAwarded) {
}