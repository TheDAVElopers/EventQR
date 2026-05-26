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
                                         String adminRemarks, List<String> additionalOrganizers, long registeredCount,
                                         long enteredCount, long attendedCount, long exitedCount, long noShowCount,
                                         long totalTransactions, long successfulScans, long rejectedScans,
                                         long benefitClaims, long boothSessionVisits, long rewardRedemptions,
                                         long totalPointsAwarded, String idTemplateStatus, String rewardsStatus,
                                         long staffCount, long scanPurposesCount) {
    }

    public record OrganizerDashboardResponse(OrganizerEventResponse event) {
    }

    public record OrganizerAttendeeResponse(UUID attendeeId, UUID registrationId, UUID eventId, UUID qrCredentialId,
                                            String name, String email, String phone, String registrationStatus,
                                            String currentEventStatus, int points, String lastTransactionTime,
                                            String registeredDate, String qrCredentialStatus,
                                            List<String> recentTransactions, List<String> recentRejectedScans) {
    }

    public record OrganizerTransactionResponse(UUID transactionId, UUID eventId, String eventTitle, UUID attendeeId,
                                               String attendeeName, UUID registrationId, UUID qrCredentialId,
                                               UUID scanPurposeId, UUID staffId, String staffName, String qrId,
                                               String scanPurpose, TransactionType transactionType,
                                               TransactionResult resultStatus, int pointsDelta, String reason,
                                               String message, String deviceSource, String relatedItem,
                                               Instant createdTimestamp) {
    }

    public record OrganizerReportResponse(UUID eventId, long totalRegistered, long enteredCount, long noShowCount,
                                          long pointsDistributed, long benefitClaims, long boothSessionVisits,
                                          long rewardRedemptions, long rejectedScans, List<ReportRow> transactionSummary,
                                          List<ReportRow> attendanceSummary, List<ReportRow> rejectedSummary,
                                          List<ReportRow> pointsRewardsSummary, List<ReportRow> recentActivity) {
    }

    public record ReportRow(String label, String value) {
    }

    public record OrganizerStaffResponse(UUID assignmentId, UUID eventId, UUID staffUserId, String name, String email,
                                         String roleLabel, boolean active, List<String> permissions, Instant addedAt) {
    }

    public record StaffAssignmentRequest(UUID staffUserId, String email, String name, String roleLabel,
                                         List<String> permissions) {
    }

    public record StaffAssignmentUpdateRequest(Boolean active, String roleLabel, List<String> permissions) {
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
}
