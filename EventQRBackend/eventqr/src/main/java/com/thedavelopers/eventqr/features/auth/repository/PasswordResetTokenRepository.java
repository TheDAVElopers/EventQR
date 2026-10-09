package com.thedavelopers.eventqr.features.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Latest unused, unexpired token of the user, row-locked so concurrent guesses serialize on the counter. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PasswordResetToken p WHERE p.userId = :userId AND p.used = false AND p.expiresAt > :now "
            + "ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PasswordResetToken> findLatestActiveForUpdate(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.userId = :userId AND p.used = false")
    int invalidateAllUnusedByUserId(UUID userId);

    /**
     * Bulk purge of expired tokens (used or unused). Called hourly by the scheduler so
     * the password_reset_tokens table cannot grow unbounded.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
