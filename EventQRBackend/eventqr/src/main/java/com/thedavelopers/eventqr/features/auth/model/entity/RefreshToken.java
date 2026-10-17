package com.thedavelopers.eventqr.features.auth.model.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One single-use refresh token, identified by the SHA-256 hex digest of its value. */
@Getter
@Setter
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** All tokens descended from one login share a family so a reuse can revoke the lot. */
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** V43. token_hash of the successor issued when this token was rotated; null if never rotated. */
    @Column(name = "replaced_by_hash", length = 64)
    private String replacedByHash;

    /** V43. When the login that started this family happened; copied unchanged to every successor. */
    @Column(name = "family_created_at", nullable = false, updatable = false)
    private Instant familyCreatedAt;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this(userId, familyId, tokenHash, createdAt, expiresAt, createdAt);
    }

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt,
                        Instant familyCreatedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.familyCreatedAt = familyCreatedAt;
    }
}
