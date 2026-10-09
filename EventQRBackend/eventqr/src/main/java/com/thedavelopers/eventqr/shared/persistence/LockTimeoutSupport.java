package com.thedavelopers.eventqr.shared.persistence;

import org.springframework.data.jpa.repository.Query;

/**
 * Mixed into repositories whose callers take row (SELECT ... FOR UPDATE) or advisory locks. Hibernate cannot
 * express a millisecond lock wait in Postgres FOR UPDATE syntax, and the JPA lock-timeout hint never applies to
 * pg_advisory_xact_lock, so the wait is bounded with a transaction-local lock_timeout instead. It covers every
 * lock wait in the transaction (row and advisory) and resets at commit/rollback, so pooled connections, other
 * transactions and Flyway are unaffected.
 *
 * A wait that exceeds it fails with SQLSTATE 55P03, which surfaces as a lock-failure exception that
 * GlobalExceptionHandler maps to a retryable 409 + Retry-After instead of holding a pooled connection forever.
 */
public interface LockTimeoutSupport {

    /** Bound on any single lock wait. Scans and redemptions hold their locks for milliseconds. */
    String LOCK_TIMEOUT = "3s";

    /** SET LOCAL lock_timeout for the current transaction. Must be called inside a transaction. */
    @Query(value = "SELECT set_config('lock_timeout', :timeout, true)", nativeQuery = true)
    String setLocalLockTimeout(@org.springframework.data.repository.query.Param("timeout") String timeout);

    /** {@link #setLocalLockTimeout(String)} with the standard {@link #LOCK_TIMEOUT}. */
    default void boundLockWaits() {
        setLocalLockTimeout(LOCK_TIMEOUT);
    }
}
