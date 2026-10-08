package com.thedavelopers.eventqr.features.registrations.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    boolean existsByEventIdAndAttendeeEmailIgnoreCase(UUID eventId, String attendeeEmail);

    boolean existsByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId);

    Optional<EventRegistration> findByEventIdAndAttendeeEmailIgnoreCase(UUID eventId, String attendeeEmail);

    Optional<EventRegistration> findByQrCredentialId(UUID qrCredentialId);

    Optional<EventRegistration> findByEventIdAndRegistrationNumber(UUID eventId, Integer registrationNumber);

    List<EventRegistration> findByEventId(UUID eventId);

    Page<EventRegistration> findByEventId(UUID eventId, Pageable pageable);

    // Server-side search: :q is a lower-case pattern from LikePatterns (escape '!'); statuses is never empty.
    @Query(value = "select r from EventRegistration r where r.eventId = :eventId and r.status in :statuses "
            + "and (lower(r.attendeeName) like :q escape '!' or lower(r.attendeeEmail) like :q escape '!' "
            + "or cast(r.registrationNumber as string) like :q escape '!') "
            + "order by r.registeredAt desc, r.id asc",
           countQuery = "select count(r) from EventRegistration r where r.eventId = :eventId and r.status in :statuses "
            + "and (lower(r.attendeeName) like :q escape '!' or lower(r.attendeeEmail) like :q escape '!' "
            + "or cast(r.registrationNumber as string) like :q escape '!')")
    Page<EventRegistration> searchByEvent(@Param("eventId") UUID eventId, @Param("q") String q,
                                          @Param("statuses") java.util.Collection<RegistrationStatus> statuses,
                                          Pageable pageable);

    List<EventRegistration> findByAttendeeUserId(UUID attendeeUserId);

    @Query(value = "select r from EventRegistration r, com.thedavelopers.eventqr.features.events.model.entity.Event e where e.id = r.eventId and r.attendeeUserId = :attendeeUserId "
            + "order by e.eventStartAt asc, r.id asc",
           countQuery = "select count(r) from EventRegistration r where r.attendeeUserId = :attendeeUserId")
    Page<EventRegistration> findByAttendeeUserId(@org.springframework.data.repository.query.Param("attendeeUserId") UUID attendeeUserId, Pageable pageable);

    Page<EventRegistration> findByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId, Pageable pageable);

    // Counts every registration row for the event (matches currentAttendeeCount semantics
    // used across organizer reporting; no status filtering).
    long countByEventId(UUID eventId);

    /**
     * Atomic registration status transition with optimistic guard.
     * Only transitions if the current status matches :expectedStatus.
     * Returns 1 if transitioned, 0 if precondition failed (lost-update or invalid transition).
     * (Design §5.6)
     */
    @Modifying
    @Query(value = "UPDATE event_registrations SET status = :newStatus, updated_at = now() " +
                   "WHERE id = :id AND status = :expectedStatus", nativeQuery = true)
    int updateStatusIfCurrent(@Param("id") UUID id,
                              @Param("expectedStatus") String expectedStatus,
                              @Param("newStatus") String newStatus);

    /**
     * Increment points earned on a registration atomically.
     */
    @Modifying
    @Query(value = "UPDATE event_registrations SET points_earned = points_earned + :delta, updated_at = now() " +
                   "WHERE id = :id", nativeQuery = true)
    int addPointsEarned(@Param("id") UUID id, @Param("delta") int delta);

    /**
     * Count registrations for an event with a specific status (cheaper than loading).
     */
    long countByEventIdAndStatus(UUID eventId, RegistrationStatus status);
}
