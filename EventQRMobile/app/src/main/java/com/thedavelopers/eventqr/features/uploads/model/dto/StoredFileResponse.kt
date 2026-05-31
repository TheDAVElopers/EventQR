package com.thedavelopers.eventqr.features.uploads.model.dto

import java.time.Instant
import java.util.UUID

data class StoredFileResponse(
    val fileId: UUID,
    val ownerId: UUID? = null,
    val purpose: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val size: Long = 0,
    val status: String? = null,
    val storedAt: Instant? = null,
    val contentBase64: String? = null,
)