package com.thedavelopers.eventqr.features.idprinting.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "id_print_logs")
public class IdPrintLog extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID attendeeUserId;

    @Column(nullable = false)
    private UUID registrationId;

    @Column(nullable = false)
    private UUID qrCredentialId;

    @Column(nullable = false)
    private UUID templateId;

    @Column(nullable = false)
    private boolean reprint;

    @Column(nullable = false)
    private boolean success;

    private Instant printedAt;

    @Column(length = 2000)
    private String message;
}