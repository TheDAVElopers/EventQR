package com.thedavelopers.eventqr.features.rewards.model.entity;

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
@Table(name = "point_transactions")
public class PointTransaction extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID attendeeUserId;

    @Column(nullable = false)
    private UUID sourceTransactionId;

    @Column(nullable = false)
    private int pointsChanged;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = 2000)
    private String reason;
}