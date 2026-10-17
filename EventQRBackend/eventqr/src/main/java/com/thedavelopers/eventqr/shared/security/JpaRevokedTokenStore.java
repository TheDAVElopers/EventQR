package com.thedavelopers.eventqr.shared.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaRevokedTokenStore implements RevokedTokenStore {

    private static final long PURGE_INTERVAL_MS = 60L * 60L * 1000L;

    /**
     * Bounds one expired-token purge so a stalled delete cannot pin the single scheduler thread that every
     * {@code @Scheduled} job shares (spring.task.scheduling.pool.size=1). The purge is a single idempotent
     * DELETE, so a timeout that rolls it back simply retries on the next hourly run.
     */
    private static final int PURGE_TIMEOUT_SECONDS = 30;

    private final RevokedTokenRepository repository;

    public JpaRevokedTokenStore(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(String tokenHash, Instant expiresAt) {
        // save() is an upsert by primary key, so revoking the same token twice is harmless.
        repository.save(new RevokedToken(tokenHash, expiresAt));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findActive(String tokenHash) {
        return repository.findById(tokenHash)
                .map(RevokedToken::getExpiresAt)
                .filter(expiresAt -> expiresAt.isAfter(Instant.now()));
    }

    /** Expired tokens can no longer authenticate, so their denylist rows are dead weight. */
    @Scheduled(fixedDelay = PURGE_INTERVAL_MS, initialDelay = PURGE_INTERVAL_MS)
    @Transactional(timeout = PURGE_TIMEOUT_SECONDS)
    public void purgeExpired() {
        repository.deleteExpired(Instant.now());
    }
}
