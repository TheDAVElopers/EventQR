package com.thedavelopers.eventqr.features.organizer

import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode

data class OrganizerMvpScanPurpose(
    val label: String,
    val description: String,
    val enabled: Boolean,
    val duplicateRule: String,
    val trackingOnly: Boolean,
    val pointsEnabled: Boolean,
    val pointsValue: Int,
    val requiredSelectionLabel: String,
    val id: String? = null,
    val code: ScanPurposeCode? = null,
)
