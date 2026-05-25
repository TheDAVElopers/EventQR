package com.thedavelopers.eventqr.features.registrations.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.entity.BaseEntity;

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
@Table(name = "event_registrations")
public class EventRegistration extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID attendeeUserId;

    @Column(nullable = false)
    private String attendeeEmail;

    @Column(nullable = false)
    private String attendeeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    private UUID qrCredentialId;

    private Instant registeredAt;

    private Instant enteredAt;

    private Instant exitedAt;

    private Instant attendedAt;

    @Column(nullable = false)
    private Integer pointsEarned = 0;
}