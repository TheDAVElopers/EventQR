package com.thedavelopers.eventqr.features.staff.result

/** What the scan-result screen should show for a verified (or rejected) scan. */
enum class ScanResultState {
    /** Verified and the QR / registration is active: transaction can be logged. */
    ACTIVE,

    /** Verified attendee, but the QR credential or registration is not active: nothing may be logged. */
    INACTIVE,

    /** QR and registration are active, but the backend says this scan would be rejected (duplicate, rewards off, event not active). */
    NOT_ELIGIBLE,

    /** Verification itself was rejected. */
    REJECTED;

    companion object {
        private val INACTIVE_REGISTRATION_STATUSES = setOf("CANCELLED", "NO_SHOW")

        /** [eligible] defaults to true: null/absent from an older backend means eligible. */
        fun from(
            isValid: Boolean,
            qrActive: Boolean,
            registrationStatus: String?,
            eligible: Boolean = true,
        ): ScanResultState = when {
            !isValid -> REJECTED
            !qrActive || registrationStatus?.uppercase() in INACTIVE_REGISTRATION_STATUSES -> INACTIVE
            !eligible -> NOT_ELIGIBLE
            else -> ACTIVE
        }
    }
}
