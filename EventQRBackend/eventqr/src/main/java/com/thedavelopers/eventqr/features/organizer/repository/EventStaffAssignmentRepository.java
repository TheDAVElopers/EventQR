package com.thedavelopers.eventqr.features.organizer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;

public interface EventStaffAssignmentRepository extends JpaRepository<EventStaffAssignment, UUID> {

    @Query("select assignment from EventStaffAssignment assignment where assignment.eventId = :eventId and assignment.active = true")
    List<EventStaffAssignment> findByEventId(@Param("eventId") UUID eventId);

    List<EventStaffAssignment> findByEventIdAndActiveTrue(UUID eventId);

    List<EventStaffAssignment> findByStaffUserId(UUID staffUserId);

    List<EventStaffAssignment> findByStaffUserIdAndActiveTrue(UUID staffUserId);

    Optional<EventStaffAssignment> findByEventIdAndStaffUserId(UUID eventId, UUID staffUserId);

    Optional<EventStaffAssignment> findByEventIdAndStaffUserIdAndActiveTrue(UUID eventId, UUID staffUserId);

    boolean existsByEventIdAndStaffUserId(UUID eventId, UUID staffUserId);

    boolean existsByEventIdAndStaffUserIdAndActiveTrue(UUID eventId, UUID staffUserId);

    boolean existsByStaffUserIdAndActiveTrue(UUID staffUserId);
}
