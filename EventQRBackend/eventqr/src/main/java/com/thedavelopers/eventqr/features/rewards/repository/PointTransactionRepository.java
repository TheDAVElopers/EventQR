package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {

    List<PointTransaction> findByEventId(UUID eventId);

    List<PointTransaction> findByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId);

    long countByEventId(UUID eventId);

    /**
     * Sum of POSITIVE points_changed grouped per (event, attendee), for the given event and attendee id
     * sets. Callers pick the pairs they need from the result (a superset of the requested pairs).
     */
    @Query("SELECT p.eventId AS eventId, p.attendeeUserId AS attendeeUserId, SUM(p.pointsChanged) AS total "
            + "FROM PointTransaction p WHERE p.eventId IN :eventIds AND p.attendeeUserId IN :attendeeUserIds "
            + "AND p.pointsChanged > 0 GROUP BY p.eventId, p.attendeeUserId")
    List<EarnedPointsRow> sumEarnedPoints(@Param("eventIds") Collection<UUID> eventIds,
                                          @Param("attendeeUserIds") Collection<UUID> attendeeUserIds);

    interface EarnedPointsRow {
        UUID getEventId();

        UUID getAttendeeUserId();

        Long getTotal();
    }
}