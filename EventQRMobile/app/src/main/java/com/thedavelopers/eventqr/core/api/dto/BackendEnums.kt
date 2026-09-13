package com.thedavelopers.eventqr.core.api.dto

enum class AccountRole {
    ATTENDEE,
    USER,
    ORGANIZER,
    STAFF,
    ADMIN,
    SUPER_ADMIN,
}

enum class AccountStatus {
    ACTIVE,
    INACTIVE,
    PENDING,
    SUSPENDED,
}

// DTO enum fields non-null-no-default — safe only while all statuses NOT NULL in DB. If nullable status introduced, add Gson TypeAdapter.
enum class EventStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    ACTIVE,
    ENDED,
    CANCELLED,
    UNKNOWN,
}

enum class EventRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

enum class RegistrationStatus {
    REGISTERED,
    ENTERED,
    EXITED,
    CANCELLED,
    NO_SHOW,
}

enum class TransactionResult {
    APPROVED,
    REJECTED,
}

enum class TransactionType {
    ENTRY,
    ATTENDANCE,
    BENEFIT_CLAIM,
    BOOTH_VISIT,
    SESSION_VISIT,
    REWARD_REDEMPTION_SCAN,
    REWARD_REDEMPTION,
    EXIT,
    ID_PRINT,
    REGISTRATION,
}

enum class ScanPurposeCode {
    ENTRY,
    ATTENDANCE,
    BENEFIT_CLAIM,
    BOOTH_VISIT,
    SESSION_VISIT,
    REWARD_REDEMPTION_SCAN,
    REWARD_REDEMPTION,
    EXIT,
    ID_PRINT,
    REGISTRATION_LOOKUP,
}

enum class NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    READ,
}

enum class NotificationType {
    GENERAL,
    SCAN_APPROVED,
    SCAN_REJECTED,
    STAFF_ASSIGNMENT,
    REGISTRATION_NEW,
    CAPACITY_WARNING,
    CAPACITY_FULL,
    REWARD_EXHAUSTED,
    POINTS_ADJUSTED,
    EVENT_APPROVED,
    EVENT_REJECTED,
    EVENT_STARTING_SOON,
    EVENT_COMPLETED,
}

enum class RewardStatus {
    ACTIVE,
    INACTIVE,
}

enum class RedemptionStatus {
    PENDING,
    REDEEMED,
    REJECTED,
}

enum class QrDisplayStatus {
    PENDING,
    SHOWN_ONCE,
    REVOKED,
}

enum class QrDeliveryStatus {
    PENDING,
    QUEUED,
    SENT,
    FAILED,
}
