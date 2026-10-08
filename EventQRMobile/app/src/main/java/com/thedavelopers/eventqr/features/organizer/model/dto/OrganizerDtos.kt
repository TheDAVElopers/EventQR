package com.thedavelopers.eventqr.features.organizer.model.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import java.time.Instant
import java.util.UUID

data class OrganizerEventDto(
    val eventId: UUID,
    val title: String? = null,
    val organizerName: String? = null,
    val dateTime: String? = null,
    val shortDate: String? = null,
    val venue: String? = null,
    val status: String? = null,
    val submittedDate: String? = null,
    val adminRemarks: String? = null,
    val description: String? = null,
    val eventStartAt: Instant? = null,
    val eventEndAt: Instant? = null,
    val registrationOpenAt: Instant? = null,
    val registrationCloseAt: Instant? = null,
    val capacity: Int = 0,
    val currentAttendeeCount: Int = 0,
    val availableSlots: Int = 0,
    val additionalOrganizers: List<String> = emptyList(),
    val registeredCount: Long = 0L,
    val enteredCount: Long = 0L,
    val attendedCount: Long = 0L,
    val exitedCount: Long = 0L,
    val noShowCount: Long = 0L,
    val totalTransactions: Long = 0L,
    val successfulScans: Long = 0L,
    val rejectedScans: Long = 0L,
    val benefitClaims: Long = 0L,
    val boothSessionVisits: Long = 0L,
    val rewardRedemptions: Long = 0L,
    val totalPointsAwarded: Long = 0L,
    val idTemplateStatus: String? = null,
    val rewardsStatus: String? = null,
    val staffCount: Long = 0L,
    val scanPurposesCount: Long = 0L,
    // Raw values needed to echo unchanged fields back on event update (UC-20).
    val rewardsEnabled: Boolean? = null,
    val organizerUserId: UUID? = null,
    val eventLogoUrl: String? = null,
)

data class OrganizerDashboardDto(
    val organizerUserId: UUID? = null,
    val organizerName: String? = null,
    val organizerEmail: String? = null,
    val organization: String? = null,
    val totalEvents: Long = 0L,
    val totalAttendees: Long = 0L,
    /** Counted-as-registered across approved/active/ended events (same number as totalAttendees). */
    val totalRegistrations: Long? = null,
    /** Count of REDEEMED reward redemptions. */
    val rewardRedemptions: Long? = null,
    val totalTransactions: Long = 0L,
    val totalPointsAwarded: Long = 0L,
    val rewardsSummary: String? = null,
    val recentEvents: List<OrganizerEventDto> = emptyList(),
    val event: OrganizerEventDto? = null,
)

data class OrganizerAttendeeDto(
    val attendeeId: UUID? = null,
    val registrationId: UUID,
    val eventId: UUID,
    val qrCredentialId: UUID? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val registrationStatus: String? = null,
    val currentEventStatus: String? = null,
    val points: Int = 0,
    val lastTransactionTime: String? = null,
    val registeredDate: String? = null,
    val qrCredentialStatus: String? = null,
    val recentTransactions: List<JsonElement> = emptyList(),
    val recentRejectedScans: List<String> = emptyList(),
    val countedAsRegistered: Boolean? = null,
)

data class OrganizerTransactionDto(
    val transactionId: UUID,
    val eventId: UUID,
    val eventTitle: String? = null,
    val attendeeId: UUID? = null,
    val attendeeName: String? = null,
    val attendeeEmail: String? = null,
    val registrationId: UUID? = null,
    val qrCredentialId: UUID? = null,
    val scanPurposeId: UUID? = null,
    val staffId: UUID? = null,
    val staffName: String? = null,
    val staffEmail: String? = null,
    val qrId: String? = null,
    val scanPurpose: String? = null,
    val transactionType: TransactionType,
    val resultStatus: TransactionResult,
    val pointsDelta: Int = 0,
    val reason: String? = null,
    val message: String? = null,
    val deviceSource: String? = null,
    val relatedItem: String? = null,
    val createdTimestamp: Instant? = null,
)

data class OrganizerStaffDto(
    val assignmentId: UUID,
    val eventId: UUID,
    val staffUserId: UUID,
    val name: String? = null,
    val email: String? = null,
    val roleLabel: String? = null,
    val active: Boolean = true,
    val canScan: Boolean = false,
    val canPrintId: Boolean = false,
    val canViewLogs: Boolean = false,
    val canManageRewards: Boolean = false,
    val permissions: List<String> = emptyList(),
    val addedAt: Instant? = null,
    /** True only in the add response, when an ATTENDEE account was promoted to STAFF. */
    val promotedToStaff: Boolean = false,
)

data class StaffAssignmentRequestDto(
    val staffUserId: UUID? = null,
    val email: String? = null,
    val name: String? = null,
    val roleLabel: String? = null,
    val canScan: Boolean? = null,
    val canPrintId: Boolean? = null,
    val canViewLogs: Boolean? = null,
    val canManageRewards: Boolean? = null,
    val permissions: List<String> = emptyList(),
)

data class StaffAssignmentUpdateRequestDto(
    val active: Boolean? = null,
    val roleLabel: String? = null,
    val canScan: Boolean? = null,
    val canPrintId: Boolean? = null,
    val canViewLogs: Boolean? = null,
    val canManageRewards: Boolean? = null,
    val permissions: List<String>? = null,
)

data class OrganizerScanPurposeDto(
    @SerializedName(value = "scanPurposeId", alternate = ["purposeId", "id"])
    val scanPurposeId: UUID? = null,
    val eventId: UUID,
    @SerializedName(value = "title", alternate = ["name", "purposeName"])
    val title: String? = null,
    val description: String? = null,
    @SerializedName(value = "code", alternate = ["scanPurposeCode"])
    val code: ScanPurposeCode,
    @SerializedName(value = "enabled", alternate = ["active", "isActive"])
    val enabled: Boolean = false,
    val trackingOnly: Boolean = true,
    val pointsEnabled: Boolean = false,
    val pointsValue: Int = 0,
    val allowDuplicate: Boolean = false,
    val duplicateRuleSummary: String? = null,
    val requiredSelectionLabel: String? = null,
)

data class OrganizerScanPurposeRequestDto(
    val scanPurposeId: UUID? = null,
    val title: String,
    val code: ScanPurposeCode,
    val enabled: Boolean,
    val trackingOnly: Boolean,
    val pointsEnabled: Boolean,
    val pointsValue: Int,
    val allowDuplicate: Boolean,
    val duplicateRuleSummary: String? = null,
    val requiredSelectionLabel: String? = null,
    val description: String? = null,
)

data class OrganizerUserSearchDto(
    val userId: UUID,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val status: String? = null,
)
