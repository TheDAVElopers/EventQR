package com.thedavelopers.eventqr.features.events.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.entity.BaseEntity;
import com.thedavelopers.eventqr.shared.port.EventLookupPort.EventSnapshot;

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
@Table(name = "events")
public class Event extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String location;

    private Instant registrationOpenAt;

    private Instant registrationCloseAt;

    private Instant eventStartAt;

    private Instant eventEndAt;

    @Column(nullable = false)
    private Integer capacity = 0;

    @Column(nullable = false)
    private Integer currentAttendeeCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false)
    private boolean rewardsEnabled;

    private UUID organizerUserId;

    private UUID approvedByUserId;

    private Instant approvedAt;

    private String rejectionReason;

    public EventSnapshot toSnapshot() {
        return new EventSnapshot(getId(), title, location, status, registrationOpenAt, registrationCloseAt,
                eventStartAt, eventEndAt, safeCount(capacity), safeCount(currentAttendeeCount),
                rewardsEnabled, organizerUserId);
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}