package com.thedavelopers.eventqr.features.organizer.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class OrganizerDtos {
    private OrganizerDtos() {
    }

    public record OrganizerEventResponse(UUID eventId, String title, String organizerName, String dateTime,
                                         String shortDate, String venue, String status, String submittedDate,
                                         String adminRemarks, String description, Instant eventStartAt,
                                         Instant eventEndAt, Instant registrationOpenAt, Instant registrationCloseAt,
                                         int capacity, int currentAttendeeCount, int availableSlots,
                                         List<String> additionalOrganizers, long registeredCount,
                                         long enteredCount, long attendedCount, long exitedCount, long noShowCount,
                                         long totalTransactions, long successfulScans, long rejectedScans,
                                         long benefitClaims, long boothSessionVisits, long rewardRedemptions,
                                         long totalPointsAwarded, String idTemplateStatus, String rewardsStatus,
                                         long staffCount, long scanPurposesCount, boolean rewardsEnabled,
                                         UUID organizerUserId, String eventLogoUrl) {
    }

    public record OrganizerDashboardResponse(UUID organizerUserId, String organizerName, String organizerEmail,
                                             String organization, long totalEvents, long totalAttendees,
                                             long totalTransactions, long totalPointsAwarded,
                                             String rewardsSummary, List<OrganizerEventResponse> recentEvents,
                                             OrganizerEventResponse event, long totalRegistrations,
                                             long rewardRedemptions) {
    }

    public record OrganizerAttendeeResponse(UUID attendeeId, UUID registrationId, UUID eventId, UUID qrCredentialId,
                                            String name, String email, String phone, String registrationStatus,
                                            String currentEventStatus, int points, String lastTransactionTime,
                                            String registeredDate, String qrCredentialStatus,
                                            List<TransactionEntry> recentTransactions, List<String> recentRejectedScans,
                                            boolean countedAsRegistered) {
    }

    public record TransactionEntry(String type, String timestamp) {
    }

    public record OrganizerTransactionResponse(UUID transactionId, UUID eventId, String eventTitle, UUID attendeeId,
                                               String attendeeName, String attendeeEmail, UUID registrationId,
                                               UUID qrCredentialId, UUID scanPurposeId, UUID staffId,
                                               String staffName, String staffEmail, String qrId,
                                               String scanPurpose, TransactionType transactionType,
                                               TransactionResult resultStatus, int pointsDelta, String reason,
                                               String message, String deviceSource, String relatedItem,
                                               Instant createdTimestamp) {
    }

    public record OrganizerStaffResponse(UUID assignmentId, UUID eventId, UUID staffUserId, String name, String email,
                                         String roleLabel, boolean active, boolean canScan, boolean canPrintId,
                                         boolean canViewLogs, boolean canManageRewards,
                                         List<String> permissions, Instant addedAt, boolean promotedToStaff) {
    }

    public record StaffAssignmentRequest(UUID staffUserId, String email, String name, String roleLabel,
                                         Boolean canScan, Boolean canPrintId, Boolean canViewLogs,
                                         Boolean canManageRewards, List<String> permissions) {
    }

    public record StaffAssignmentUpdateRequest(Boolean active, String roleLabel,
                                               Boolean canScan, Boolean canPrintId, Boolean canViewLogs,
                                               Boolean canManageRewards, List<String> permissions) {
    }

    public record OrganizerScanPurposeResponse(UUID scanPurposeId, UUID eventId, String title, String description,
                                               ScanPurposeCode code, boolean enabled, boolean trackingOnly,
                                               boolean pointsEnabled, int pointsValue, boolean allowDuplicate,
                                               String duplicateRuleSummary, String requiredSelectionLabel) {
    }

    public record OrganizerScanPurposeRequest(UUID scanPurposeId, @NotBlank String title,
                                              @NotNull ScanPurposeCode code, boolean enabled, boolean trackingOnly,
                                              boolean pointsEnabled, @Min(0) int pointsValue,
                                              boolean allowDuplicate, String duplicateRuleSummary,
                                              String requiredSelectionLabel, String description) {
    }

    public record UserSearchResponse(UUID userId, String name, String email, String role, String status) {
    }

    public record OrganizerTransactionRuleResponse(UUID id, UUID eventId, UUID scanPurposeId, boolean active,
                                                   boolean allowDuplicate, int duplicateWindowMinutes,
                                                   int maxUsesPerRegistration, boolean requiresStaffAssignment,
                                                   int pointsAwarded, Instant createdAt, Instant updatedAt) {
    }
}
