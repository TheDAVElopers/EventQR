package com.thedavelopers.eventqr.features.monitor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single entry point used by the Hibernate listeners and the repository aspect to hand operations to the
 * {@link CrudEventRecorder}. It decides whether an operation is in scope and attaches request metadata.
 *
 * <p><b>Scope.</b> Only operations running inside an HTTP request that Spring MVC has mapped to a handler are
 * recorded; scheduler, async, startup and filter-phase work (for example the JWT filter's user lookup, which runs
 * before a route is known) is ignored. Requests under {@code /api/v1/monitor} are never recorded. The route is
 * the matched handler pattern (for example {@code POST /api/v1/registrations/{id}}), never the raw URL.
 *
 * <p><b>Transactions.</b> Writes seen by the Hibernate listeners fire while the session flushes, before the
 * transaction outcome is known. They are therefore buffered per transaction in a
 * {@link TransactionSynchronization} and emitted in {@code afterCompletion}: SUCCESS only if the transaction
 * committed, FAILURE otherwise (rollback or unknown outcome). Each event keeps the time the write was seen, not
 * the commit time; sequence numbers follow emission order. If a transaction exceeds the per-transaction cap, the
 * rest are replaced by one summary event ("N more writes in this transaction omitted", table
 * {@value #MULTIPLE_TABLES}). If no transaction synchronization is active (for example a repository call under a
 * non-synchronized template), the write has already executed and nothing will report a later outcome, so it is
 * emitted at once as SUCCESS. Reads and failed calls are never deferred.
 *
 * <p><b>Known limits (what can and cannot be reported).</b>
 * <ul>
 *   <li>A FAILURE is reported only for a write whose Hibernate post-write event fired before the rollback.
 *       Inserts of entities with application-generated ids (UUID) are deferred to flush, so a rollback before any
 *       flush produces no event at all, and a statement that itself fails (for example a constraint violation on
 *       an update or delete) never fires its post-event and so is not reported either.</li>
 *   <li>Changes made by database triggers or other server-side side effects are not visible.</li>
 *   <li>Native SQL is observed only through Spring Data repository methods (reads by name or {@code @Query},
 *       and {@code @Modifying}); anything executed outside a repository method is not captured.</li>
 * </ul>
 *
 * <p><b>Privacy.</b> Identifiers of {@link #REDACTED_ID_TABLES} are never emitted, and reads of
 * {@link #NO_ROW_COUNT_TABLES} carry no row count, so the feed cannot answer "does this person exist". No field values,
 * parameters, SQL text, bodies, headers or tokens are accepted or stored.
 *
 * <p><b>Safety.</b> Every public method swallows {@link Exception} (logged at debug) so monitoring can never
 * break a request; {@link Error}s are deliberately not caught.
 */
@Component
@MonitorEnabled
public class CrudEventCapture {

    /** Tables never reported (credentials, tokens, files, audit/mail trails). Lower-case. */
    public static final Set<String> EXCLUDED_TABLES = Set.of(
            "refresh_token",
            "revoked_token",
            "password_reset_tokens",
            "user_token_revocation",
            "stored_files",
            "audit_logs",
            "email_delivery_logs");

    /** Tables whose row identifiers identify a person; their events carry {@code entityId = null}. */
    public static final Set<String> REDACTED_ID_TABLES = Set.of(
            "user_profiles",
            "event_registrations",
            "qr_credentials");

    /** Reads on these tables carry no row count (no registered-or-not or "does this person hold X" oracle). */
    public static final Set<String> NO_ROW_COUNT_TABLES = Set.of(
            "user_profiles",
            "event_registrations",
            "qr_credentials");

    public static final String MULTIPLE_TABLES = "(multiple)";

    private static final String MONITOR_PATH_PREFIX = "/api/v1/monitor";
    private static final Logger log = LoggerFactory.getLogger(CrudEventCapture.class);

    private final CrudEventRecorder recorder;
    private final int maxPendingPerTransaction;

    @Autowired
    public CrudEventCapture(CrudEventRecorder recorder,
            @Value("${app.monitor.max-writes-per-transaction:1000}") int maxPendingPerTransaction) {
        this.recorder = recorder;
        this.maxPendingPerTransaction = Math.max(1, maxPendingPerTransaction);
    }

    /** True when the current thread is inside a mapped, non-monitor HTTP request. Cheap; safe to call per query. */
    public boolean isCapturing() {
        try {
            return currentRequestInfo() != null;
        } catch (Exception e) {
            log.debug("monitor context check failed", e);
            return false;
        }
    }

    /** A read; recorded immediately. */
    public void recordRead(String table, String description, Integer rowCount, boolean success) {
        try {
            RequestInfo info = currentRequestInfo();
            if (info == null || excluded(table)) {
                return;
            }
            Integer rows = NO_ROW_COUNT_TABLES.contains(table) ? null : rowCount;
            emit(new Pending(Instant.now(), CrudOperation.READ, table, null, description, info, rows),
                    success ? CrudResult.SUCCESS : CrudResult.FAILURE);
        } catch (Exception e) {
            log.debug("monitor read capture failed", e);
        }
    }

    /** A write whose outcome follows the surrounding transaction (see class comment). */
    public void recordWrite(CrudOperation operation, String table, String entityId, String description,
                            Integer rowCount) {
        try {
            RequestInfo info = currentRequestInfo();
            if (info == null || excluded(table)) {
                return;
            }
            Pending pending = new Pending(Instant.now(), operation, table, entityId, description, info, rowCount);
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                emit(pending, CrudResult.SUCCESS);
                return;
            }
            PendingSynchronization sync = null;
            for (TransactionSynchronization existing : TransactionSynchronizationManager.getSynchronizations()) {
                if (existing instanceof PendingSynchronization found) {
                    sync = found;
                    break;
                }
            }
            if (sync == null) {
                sync = new PendingSynchronization();
                TransactionSynchronizationManager.registerSynchronization(sync);
            }
            sync.add(pending);
        } catch (Exception e) {
            log.debug("monitor write capture failed", e);
        }
    }

    /** A write that threw before completing; recorded immediately as FAILURE. */
    public void recordWriteFailure(CrudOperation operation, String table, String description) {
        try {
            RequestInfo info = currentRequestInfo();
            if (info == null || excluded(table)) {
                return;
            }
            emit(new Pending(Instant.now(), operation, table, null, description, info, null), CrudResult.FAILURE);
        } catch (Exception e) {
            log.debug("monitor write-failure capture failed", e);
        }
    }

    private static boolean excluded(String table) {
        return table == null || EXCLUDED_TABLES.contains(table);
    }

    private void emit(Pending p, CrudResult result) {
        String entityId = REDACTED_ID_TABLES.contains(p.table) ? null : p.entityId;
        recorder.record(p.at, p.operation, p.table, entityId, result, p.description, p.info.route, p.info.role,
                p.rowCount);
    }

    private static RequestInfo currentRequestInfo() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(pattern instanceof String matched) || matched.isBlank() || matched.startsWith(MONITOR_PATH_PREFIX)) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(MONITOR_PATH_PREFIX)) {
            return null;
        }
        return new RequestInfo(request.getMethod() + " " + matched, currentRole());
    }

    private static String currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                return value.substring("ROLE_".length());
            }
        }
        return null;
    }

    private record RequestInfo(String route, String role) {
    }

    private record Pending(Instant at, CrudOperation operation, String table, String entityId, String description,
                           RequestInfo info, Integer rowCount) {
    }

    private final class PendingSynchronization implements TransactionSynchronization {

        private final List<Pending> pending = new ArrayList<>();
        private Pending firstDropped;
        private int dropped;

        void add(Pending p) {
            if (pending.size() < maxPendingPerTransaction) {
                pending.add(p);
            } else {
                if (firstDropped == null) {
                    firstDropped = p;
                }
                dropped++;
            }
        }

        @Override
        public void afterCompletion(int status) {
            try {
                CrudResult result = status == STATUS_COMMITTED ? CrudResult.SUCCESS : CrudResult.FAILURE;
                for (Pending p : pending) {
                    emit(p, result);
                }
                if (dropped > 0) {
                    emit(new Pending(firstDropped.at, firstDropped.operation, MULTIPLE_TABLES, null,
                            dropped + " more writes in this transaction omitted", firstDropped.info, null), result);
                }
            } catch (Exception e) {
                log.debug("monitor transaction completion capture failed", e);
            }
        }
    }
}
