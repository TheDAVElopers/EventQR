package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.shared.persistence.LockTimeoutSupport;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import jakarta.persistence.LockModeType;

public interface AttendeePointBalanceRepository extends JpaRepository<AttendeePointBalance, UUID>, LockTimeoutSupport {

    Optional<AttendeePointBalance> findByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId);

    /**
     * Loads the balance row with a pessimistic write lock (SELECT ... FOR UPDATE). Every read-modify-write of a
     * balance takes it, so two concurrent redemptions (or a redemption and a points award) for the same attendee
     * cannot both spend from the same balance snapshot. Redemption paths lock the reward row first, then this.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Callers run insertZeroIfAbsent first: FOR UPDATE cannot lock a row that does not exist yet.
    @Query("SELECT b FROM AttendeePointBalance b WHERE b.eventId = :eventId AND b.attendeeUserId = :attendeeUserId")
    Optional<AttendeePointBalance> findByEventIdAndAttendeeUserIdForUpdate(@Param("eventId") UUID eventId,
                                                                          @Param("attendeeUserId") UUID attendeeUserId);

    @Query("select coalesce(sum(b.pointsBalance), 0) from AttendeePointBalance b where b.attendeeUserId = :attendeeUserId")
    long sumPointsByAttendeeUserId(@Param("attendeeUserId") UUID attendeeUserId);

    /**
     * Creates a zero balance row unless one already exists (ON CONFLICT on the unique (event_id, attendee_user_id)
     * index). Concurrent first writers both succeed instead of one failing on the unique index; a writer racing
     * an uncommitted insert waits for it. Returns 1 if inserted, 0 if the row already existed.
     */
    @Modifying
    @Query(value = "INSERT INTO attendee_point_balances (id, event_id, attendee_user_id, points_balance, created_at, updated_at) " +
                   "VALUES (gen_random_uuid(), :eventId, :attendeeUserId, 0, now(), now()) " +
                   "ON CONFLICT (event_id, attendee_user_id) DO NOTHING",
           nativeQuery = true)
    int insertZeroIfAbsent(@Param("eventId") UUID eventId, @Param("attendeeUserId") UUID attendeeUserId);

    Page<AttendeePointBalance> findByAttendeeUserId(UUID attendeeUserId, Pageable pageable);

    Page<AttendeePointBalance> findByEventId(UUID eventId, Pageable pageable);

    /**
     * Atomic point balance delta — requires unique index (event_id, attendee_user_id).
     * Returns 1 if updated, 0 if no matching row found.
     * (Design §5.3)
     */
    @Modifying
    @Query("UPDATE AttendeePointBalance b SET b.pointsBalance = b.pointsBalance + :delta, b.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE b.eventId = :eventId AND b.attendeeUserId = :attendeeUserId")
    int addPointsDelta(@Param("eventId") UUID eventId,
                       @Param("attendeeUserId") UUID attendeeUserId,
                       @Param("delta") int delta);

    /**
     * Atomic upsert: insert-or-add. Uses DB ON CONFLICT for the unique constraint.
     * (Design §5.4)
     */
    @Modifying
    @Query(value = "INSERT INTO attendee_point_balances (id, event_id, attendee_user_id, points_balance, created_at, updated_at) " +
                   "VALUES (gen_random_uuid(), :eventId, :attendeeUserId, :points, now(), now()) " +
                   "ON CONFLICT (event_id, attendee_user_id) DO UPDATE " +
                   "SET points_balance = attendee_point_balances.points_balance + EXCLUDED.points_balance, " +
                   "updated_at = now()",
           nativeQuery = true)
    int upsertPoints(@Param("eventId") UUID eventId,
                     @Param("attendeeUserId") UUID attendeeUserId,
                     @Param("points") int points);
}
