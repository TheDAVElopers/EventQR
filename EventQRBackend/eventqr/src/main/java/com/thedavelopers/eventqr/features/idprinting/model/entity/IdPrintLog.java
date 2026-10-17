package com.thedavelopers.eventqr.features.idprinting.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;
import com.thedavelopers.eventqr.shared.utils.BaseEntity;

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

    /** The staff/organizer/admin who requested the print; only they (or the organizer/admin) may confirm it. */
    @Column(name = "printed_by_user_id")
    private UUID printedByUserId;

    /** V42. New rows are written as PENDING explicitly; the column DEFAULT 'PRINTED' only covers legacy inserts. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IdPrintStatus status = IdPrintStatus.PENDING;

    /** V42. Set when the outcome is confirmed through PATCH .../print-logs/{logId}; null while PENDING. */
    private Instant confirmedAt;
}
