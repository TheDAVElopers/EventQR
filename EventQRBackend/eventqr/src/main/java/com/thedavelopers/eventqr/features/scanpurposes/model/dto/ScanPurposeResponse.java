package com.thedavelopers.eventqr.features.scanpurposes.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;

public record ScanPurposeResponse(UUID scanPurposeId, UUID eventId, String name, ScanPurposeCode code, boolean active,
                                  boolean trackingOnly, String description) {
}