package com.thedavelopers.eventqr.features.idprinting.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintLog;
import com.thedavelopers.eventqr.shared.persistence.LockTimeoutSupport;

import jakarta.persistence.LockModeType;

public interface IdPrintLogRepository extends JpaRepository<IdPrintLog, UUID>, LockTimeoutSupport {

    List<IdPrintLog> findByEventId(UUID eventId);

    /**
     * Whether the attendee has an effectively printed ID for the event: a PRINTED log, or a PENDING log created
     * before {@code staleBefore} (legacy clients never confirm, see IdPrintingService).
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM id_print_logs WHERE event_id = :eventId "
            + "AND attendee_user_id = :attendeeUserId "
            + "AND (status = 'PRINTED' OR (status = 'PENDING' AND created_at < :staleBefore)))", nativeQuery = true)
    boolean existsEffectivelyPrinted(@Param("eventId") UUID eventId, @Param("attendeeUserId") UUID attendeeUserId,
                                     @Param("staleBefore") java.time.Instant staleBefore);

    /**
     * Loads a log of the given event with SELECT ... FOR UPDATE so two concurrent confirmations of the same PENDING
     * row serialize and the second one sees the final status (409) instead of overwriting it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM IdPrintLog l WHERE l.id = :id AND l.eventId = :eventId")
    Optional<IdPrintLog> findByIdAndEventIdForUpdate(@Param("id") UUID id, @Param("eventId") UUID eventId);
}
