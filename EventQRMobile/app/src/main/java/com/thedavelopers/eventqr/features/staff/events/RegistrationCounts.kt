package com.thedavelopers.eventqr.features.staff

/** Header tiles of the registrations screen (server counts). A null field could not be loaded and renders as "--". */
data class RegistrationCounts(
    val total: Long? = null,
    val checkedIn: Long? = null,
    val registered: Long? = null,
)
