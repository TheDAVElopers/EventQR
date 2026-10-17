package com.thedavelopers.eventqr.features.users.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * DB-backed record of the last time all of a user's bearer tokens were revoked: when the
 * account was disabled/suspended, or when the password was changed or reset (see
 * {@code AccessTokenRevoker}). A row exists only once one of those happened and is retained
 * across re-enables so tokens from before the freeze do not resurrect without a fresh login.
 */
@Getter
@Setter
@Entity
@Table(name = "user_token_revocation")
public class UserTokenRevocation {

    /** The owning user's id (also the primary key). */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Instant of the last disable/suspend or password change/reset. Tokens issued at or before it are rejected. */
    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected UserTokenRevocation() {
        // JPA
    }

    public UserTokenRevocation(UUID userId, Instant revokedAt) {
        this.userId = userId;
        this.revokedAt = revokedAt;
    }
}
