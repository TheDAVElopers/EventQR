package com.thedavelopers.eventqr.features.users.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation;

public interface UserTokenRevocationRepository extends JpaRepository<UserTokenRevocation, UUID> {

    Optional<UserTokenRevocation> findByUserId(UUID userId);

    /**
     * Race-safe upsert of the user's revocation marker that never moves it backwards: two concurrent first-time
     * writers both succeed (one inserts, the other updates) instead of one failing on the primary key. Returns the
     * effective marker as epoch milliseconds, floored, so a value minted strictly after it is also strictly after the
     * stored (possibly sub-millisecond) marker.
     */
    @Query(value = "INSERT INTO user_token_revocation (user_id, revoked_at) VALUES (:userId, :revokedAt) "
            + "ON CONFLICT (user_id) DO UPDATE "
            + "SET revoked_at = GREATEST(user_token_revocation.revoked_at, EXCLUDED.revoked_at) "
            + "RETURNING CAST(floor(extract(epoch FROM revoked_at) * 1000) AS bigint)", nativeQuery = true)
    long upsertNotBackwards(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
