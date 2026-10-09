package com.thedavelopers.eventqr.shared.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.thedavelopers.eventqr.shared.response.ErrorResponse;

/** Lock timeouts/deadlocks and a same-key idempotency collision are retryable conflicts, not server errors. */
class GlobalExceptionHandlerLockFailureTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");

    private static void assertRetryableConflict(ResponseEntity<ErrorResponse> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.RETRYABLE_CONFLICT_MESSAGE);
    }

    @Test
    void lockTimeoutOrDeadlockIsARetryable409() {
        assertRetryableConflict(handler.handleLockFailure(
                new CannotAcquireLockException("could not obtain lock"), request));
        assertRetryableConflict(handler.handleLockFailure(
                new PessimisticLockingFailureException("deadlock detected"), request));
        assertRetryableConflict(handler.handleLockFailure(
                new jakarta.persistence.LockTimeoutException("lock timeout"), request));
    }

    @Test
    void clientRequestIdIndexCollisionIsARetryable409() {
        DataIntegrityViolationException collision = new DataIntegrityViolationException("insert failed",
                new RuntimeException("duplicate key value violates unique constraint \"ux_transaction_logs_client_request_id\""));

        assertRetryableConflict(handler.handleDataIntegrity(collision, request));
    }

    @Test
    void otherIntegrityErrorsKeepTheirExistingMapping() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("x", new RuntimeException("null value in column \"metadata\"")),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
        assertThat(response.getBody().message()).isEqualTo("Transaction failed: metadata missing.");
    }

    @Test
    void postgresLockTimeoutSqlStateTranslatesToALockFailureThatMapsToARetryable409() {
        // What a wait beyond SET LOCAL lock_timeout raises: SQLSTATE 55P03 (lock_not_available).
        java.sql.SQLException timeout = new java.sql.SQLException("canceling statement due to lock timeout", "55P03");

        // Hibernate's Postgres dialect classifies it as a lock failure ...
        RuntimeException hibernate = new org.hibernate.dialect.PostgreSQLDialect().buildSQLExceptionConversionDelegate()
                .convert(timeout, "lock timeout", "select ... for update");
        assertThat(hibernate).isNotNull();
        // ... which Spring's repository exception translation turns into a PessimisticLockingFailureException.
        RuntimeException translated = new org.springframework.orm.jpa.vendor.HibernateJpaDialect()
                .translateExceptionIfPossible(hibernate);
        assertThat(translated).isInstanceOf(PessimisticLockingFailureException.class);
        assertRetryableConflict(handler.handleLockFailure(translated, request));

        // The JPA form (EntityManager.refresh under a lock) maps to CannotAcquireLockException.
        RuntimeException jpa = org.springframework.orm.jpa.EntityManagerFactoryUtils
                .convertJpaAccessExceptionIfPossible(new jakarta.persistence.LockTimeoutException("lock timeout"));
        assertThat(jpa).isInstanceOf(CannotAcquireLockException.class);
        assertRetryableConflict(handler.handleLockFailure(jpa, request));
    }

    @Test
    void unhandledExceptionsStillReturnTheGenericBody() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new IllegalStateException("boom for jane.doe@example.com"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("jane.doe", "boom");
    }
}
