package com.thedavelopers.eventqr.shared.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;

public interface ScanPurposePort {

    ScanPurposeSnapshot requireActive(UUID scanPurposeId);

    Optional<ScanPurposeSnapshot> findById(UUID scanPurposeId);

    List<ScanPurposeSnapshot> listByEventId(UUID eventId);

    record ScanPurposeSnapshot(UUID scanPurposeId, UUID eventId, String name, ScanPurposeCode code, boolean active,
                               boolean trackingOnly, String description) {
    }
}