package com.thedavelopers.eventqr.features.rewards.model.entity;

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
@Table(name = "point_rules")
public class PointRule extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID scanPurposeId;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private boolean active = true;
}