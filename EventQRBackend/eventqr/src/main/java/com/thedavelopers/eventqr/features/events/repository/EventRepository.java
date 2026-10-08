package com.thedavelopers.eventqr.features.events.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByOrganizerUserId(UUID organizerUserId);

    Page<Event> findByOrganizerUserId(UUID organizerUserId, Pageable pageable);

    List<Event> findByStatusInOrderByEventStartAtAsc(Collection<EventStatus> statuses);

    Page<Event> findByStatusIn(Collection<EventStatus> statuses, Pageable pageable);

    List<Event> findTop3ByStatusInAndEventStartAtAfterOrderByEventStartAtAsc(Collection<EventStatus> statuses, Instant eventStartAt);

    Optional<Event> findFirstByOrganizerUserIdAndTitleAndEventStartAtAndLocation(UUID organizerUserId,
                                                                               String title,
                                                                               Instant eventStartAt,
                                                                               String location);

    long countByStatusIn(Collection<EventStatus> statuses);

    long countByStatus(EventStatus status);

    List<Event> findByStatusAndEventStartAtLessThanEqual(EventStatus status, Instant eventStartAt);

    List<Event> findByStatusAndEventEndAtLessThanEqual(EventStatus status, Instant eventEndAt);

    /**
     * Atomic capacity increment with oversell guard. Returns 1 if accepted, 0 if at capacity.
     * Events with capacity = 0 are unlimited and always accept.
     * (Design §5.1)
     */
    @Modifying
    @Query("UPDATE Event e SET e.currentAttendeeCount = e.currentAttendeeCount + 1, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.id = :eventId AND (e.capacity = 0 OR e.currentAttendeeCount < e.capacity)")
    int incrementAttendeeCountIfAvailable(@Param("eventId") UUID eventId);

    /**
     * Atomic capacity decrement (cancellation). Clamped at 0.
     * (Design §5.2)
     */
    @Modifying
    @Query(value = "UPDATE events SET current_attendee_count = GREATEST(current_attendee_count - 1, 0), " +
                   "updated_at = now() WHERE id = :eventId", nativeQuery = true)
    int decrementAttendeeCount(@Param("eventId") UUID eventId);

    /**
     * Bulk status transition for the scheduler. Avoids loading rows into memory.
     */
    @Modifying
    @Query("UPDATE Event e SET e.status = :newStatus, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.status = :currentStatus AND e.eventEndAt < :cutoff")
    int bulkUpdateStatusForEndedEvents(@Param("currentStatus") EventStatus currentStatus,
                                       @Param("newStatus") EventStatus newStatus,
                                       @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE Event e SET e.status = :newStatus, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.status = :currentStatus AND e.eventStartAt <= :cutoff")
    int bulkUpdateStatusForStartedEvents(@Param("currentStatus") EventStatus currentStatus,
                                         @Param("newStatus") EventStatus newStatus,
                                         @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE Event e SET e.status = :newStatus, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.status = :currentStatus AND e.eventEndAt <= :cutoff")
    int bulkUpdateStatusForFinishedEvents(@Param("currentStatus") EventStatus currentStatus,
                                          @Param("newStatus") EventStatus newStatus,
                                          @Param("cutoff") Instant cutoff);
}
