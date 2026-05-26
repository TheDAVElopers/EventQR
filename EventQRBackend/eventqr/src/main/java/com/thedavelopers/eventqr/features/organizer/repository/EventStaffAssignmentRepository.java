package com.thedavelopers.eventqr.features.organizer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;

public interface EventStaffAssignmentRepository extends JpaRepository<EventStaffAssignment, UUID> {

    List<EventStaffAssignment> findByEventId(UUID eventId);

    Optional<EventStaffAssignment> findByEventIdAndStaffUserId(UUID eventId, UUID staffUserId);

    boolean existsByEventIdAndStaffUserId(UUID eventId, UUID staffUserId);
}
