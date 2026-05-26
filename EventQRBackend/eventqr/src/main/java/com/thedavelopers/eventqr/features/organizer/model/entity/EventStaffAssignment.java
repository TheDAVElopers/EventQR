package com.thedavelopers.eventqr.features.organizer.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "event_staff_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_event_staff_assignment", columnNames = {"event_id", "staff_user_id"}))
public class EventStaffAssignment extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID staffUserId;

    @Column(nullable = false)
    private String roleLabel = "Scanner";

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String permissions;

    private UUID addedByUserId;

    @Column(nullable = false)
    private Instant addedAt = Instant.now();
}
