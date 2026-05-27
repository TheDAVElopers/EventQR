package com.thedavelopers.eventqr.features.uploads.model.dto;

import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(UUID fileId, UUID ownerId, String purpose, String fileName,
                                 String contentType, long size, String status, Instant storedAt) {
}