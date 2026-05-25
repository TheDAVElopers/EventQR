package com.thedavelopers.eventqr.features.scanpurposes.model.entity;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.entity.BaseEntity;
import com.thedavelopers.eventqr.shared.port.ScanPurposePort.ScanPurposeSnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "scan_purposes")
public class ScanPurpose extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanPurposeCode code;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean trackingOnly;

    private String description;

    public ScanPurposeSnapshot toSnapshot() {
        return new ScanPurposeSnapshot(getId(), eventId, name, code, active, trackingOnly, description);
    }
}