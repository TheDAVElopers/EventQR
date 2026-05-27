package com.thedavelopers.eventqr.features.events.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;
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
@Table(name = "event_requests")
public class EventCreationRequest extends BaseEntity {

    @Column(nullable = false)
    private UUID requesterUserId;

    @Column(nullable = false)
    private String eventName;

    @Column(nullable = false, length = 3000)
    private String eventDescription;

    @Column(nullable = false)
    private String eventCategory;

    private String targetAudience;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private Instant startDateTime;

    @Column(nullable = false)
    private Instant endDateTime;

    private Instant registrationStartDateTime;

    private Instant registrationEndDateTime;

    @Column(nullable = false)
    private String requesterName;

    @Column(nullable = false)
    private String contactEmail;

    @Column(nullable = false)
    private String contactNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> requestedFeatures = new ArrayList<>();

    private String eventLogoUrl;

    @Column(length = 3000)
    private String additionalNotes;

    @Column(nullable = false, length = 3000)
    private String reasonForRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventRequestStatus status = EventRequestStatus.PENDING;

    @Column(length = 2000)
    private String adminRemarks;

    private UUID reviewedByUserId;

    private Instant reviewedAt;
}
