package com.thedavelopers.eventqr.features.organizer.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.utils.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "role_label", nullable = false)
    private String roleLabel = "Staff";

    @Column(name = "staff_role", nullable = false)
    private String staffRole = "STAFF";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "can_scan", nullable = false)
    private boolean canScan = true;

    @Column(name = "can_print_id", nullable = false)
    private boolean canPrintId = false;

    @Column(name = "can_view_logs", nullable = false)
    private boolean canViewLogs = false;

    @Column(name = "can_manage_rewards", nullable = false)
    private boolean canManageRewards = false;

    @Column(length = 2000)
    private String permissions;

    @Column(name = "added_by_user_id")
    private UUID addedByUserId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void ensureDefaults() {
        if (roleLabel == null || roleLabel.isBlank()) {
            roleLabel = "Staff";
        }
        if (staffRole == null || staffRole.isBlank() || !"STAFF".equalsIgnoreCase(staffRole)) {
            staffRole = "STAFF";
        }
        if (permissions == null || permissions.isBlank()) {
            permissions = "Scan QR,View attendee details";
        }
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }
}

