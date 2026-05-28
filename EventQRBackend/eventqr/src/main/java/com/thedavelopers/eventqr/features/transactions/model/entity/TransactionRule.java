package com.thedavelopers.eventqr.features.transactions.model.entity;

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
@Table(name = "transaction_rules")
public class TransactionRule extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID scanPurposeId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean allowDuplicate;

    @Column(nullable = false)
    private int duplicateWindowMinutes;

    @Column(nullable = false)
    private int maxUsesPerRegistration = 1;

    @Column(nullable = false)
    private boolean requiresStaffAssignment = true;

    @Column(nullable = false)
    private int pointsAwarded;
}