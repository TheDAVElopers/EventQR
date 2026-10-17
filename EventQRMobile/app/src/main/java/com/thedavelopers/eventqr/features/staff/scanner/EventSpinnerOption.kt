package com.thedavelopers.eventqr.features.staff

import java.time.Instant

data class EventSpinnerOption(
    val id: String,
    val label: String,
    val canScan: Boolean,
    val eventStartAt: Instant? = null,
    /** The caller's Print IDs permission for this event; ID printing is hidden without it. */
    val canPrintId: Boolean = false,
)
