package com.thedavelopers.eventqr.features.staff.result

/** What the scan-result screen should show for a verified (or rejected) scan. */
enum class ScanResultState {
    /** Verified and the QR / registration is active: transaction can be logged. */
    ACTIVE,

    /** Verified attendee, but the QR credential or registration is not active: nothing may be logged. */
    INACTIVE,

    /** Verification itself was rejected. */
    REJECTED;

    companion object {
        private val INACTIVE_REGISTRATION_STATUSES = setOf("CANCELLED", "NO_SHOW")

        fun from(isValid: Boolean, qrActive: Boolean, registrationStatus: String?): ScanResultState = when {
            !isValid -> REJECTED
            !qrActive || registrationStatus?.uppercase() in INACTIVE_REGISTRATION_STATUSES -> INACTIVE
            else -> ACTIVE
        }
    }
}
