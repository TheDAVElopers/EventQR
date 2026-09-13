package com.thedavelopers.eventqr.features.rewards.model.entity;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.RewardStatus;
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
@Table(name = "rewards")
public class Reward extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private int pointsRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardStatus status = RewardStatus.ACTIVE;

    private Integer stockQuantity;

    @Column(name = "allow_duplicate_claims")
    private boolean allowDuplicateClaims;
}
