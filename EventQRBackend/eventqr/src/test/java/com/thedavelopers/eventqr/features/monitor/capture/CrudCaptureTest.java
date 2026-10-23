package com.thedavelopers.eventqr.features.monitor.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thedavelopers.eventqr.features.monitor.aspect.RepositoryCrudAspect;
import com.thedavelopers.eventqr.features.monitor.model.CrudEvent;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventCapture;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;
import com.thedavelopers.eventqr.features.monitor.service.HibernateCrudListenerRegistrar;
import com.thedavelopers.eventqr.features.auditlogs.model.entity.AuditLog;
import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;
import com.thedavelopers.eventqr.features.auth.model.entity.RefreshToken;
import com.thedavelopers.eventqr.features.qremail.model.entity.EmailDeliveryLog;
import com.thedavelopers.eventqr.features.uploads.model.entity.StoredFile;
import com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation;
import com.thedavelopers.eventqr.shared.security.RevokedToken;
import com.thedavelopers.monitorprobe.ExcludedProbe;
import com.thedavelopers.monitorprobe.ExcludedProbeRepository;
import com.thedavelopers.monitorprobe.ProbeItem;
import com.thedavelopers.monitorprobe.ProbeItemRepository;
import com.thedavelopers.monitorprobe.RedactedProbe;
import com.thedavelopers.monitorprobe.RedactedProbeRepository;

/**
 * Capture pipeline (Hibernate listeners + repository aspect + per-transaction buffering) against an in-memory H2
 * database, using test-only entities. Real Postgres/Flyway are covered by neither this test nor Docker-less CI.
 * The class runs without a surrounding test transaction so that commits and rollbacks are real.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.monitor.token=0123456789abcdef0123456789abcdef-test",
        "app.monitor.buffer-size=2000",
        "app.monitor.max-writes-per-transaction=5"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CrudCaptureTest {

    private static final String SECRET = "SECRET-VALUE-9f2c";
    private static final String ROUTE_PATTERN = "/api/v1/probes/{id}";

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = ProbeItem.class)
    @EnableJpaRepositories(basePackageClasses = ProbeItemRepository.class)
    @ImportAutoConfiguration(AopAutoConfiguration.class)
    @Import({ CrudEventRecorder.class, CrudEventCapture.class, HibernateCrudListenerRegistrar.class,
            RepositoryCrudAspect.class })
    static class TestConfig {
    }

    @Autowired
    private CrudEventRecorder recorder;
    @Autowired
    private ProbeItemRepository items;
    @Autowired
    private ExcludedProbeRepository excluded;
    @Autowired
    private RedactedProbeRepository redacted;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private long baseline;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        // Housekeeping outside any request: must record nothing.
        items.deleteAllInBatch();
        excluded.deleteAllInBatch();
        redacted.deleteAllInBatch();
        baseline = recorder.latestSeq();
    }

    @AfterEach
    void clearContexts() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void enterRequest(String method, String pattern) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/raw/url/" + UUID.randomUUID() + "?q=secret");
        if (pattern != null) {
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    private List<CrudEvent> events() {
        return recorder.read(baseline, CrudEventRecorder.MAX_LIMIT).events();
    }

    @Test
    @DisplayName("committed insert, update and delete are reported as SUCCESS with table, id, route and role only")
    void committedWritesAreSuccess() {
        enterRequest("POST", ROUTE_PATTERN);
        ProbeItem saved = items.save(new ProbeItem(SECRET, "NEW"));
        tx.executeWithoutResult(s -> {
            ProbeItem managed = items.findById(saved.getId()).orElseThrow();
            managed.setLabel("relabelled");
            managed.setSecretValue(SECRET + "-changed");
        });
        items.deleteById(saved.getId());

        List<CrudEvent> writes = events().stream().filter(e -> e.operation() != CrudOperation.READ).toList();
        assertThat(writes).extracting(CrudEvent::operation)
                .containsExactly(CrudOperation.CREATE, CrudOperation.UPDATE, CrudOperation.DELETE);
        assertThat(writes).allSatisfy(e -> {
            assertThat(e.result()).isEqualTo(CrudResult.SUCCESS);
            assertThat(e.table()).isEqualTo("monitor_probe_items");
            assertThat(e.entityId()).isEqualTo(saved.getId().toString());
            assertThat(e.route()).isEqualTo("POST " + ROUTE_PATTERN);
            assertThat(e.actorRole()).isEqualTo("STAFF");
            assertThat(e.rowCount()).isEqualTo(1);
        });
        CrudEvent update = writes.get(1);
        assertThat(update.description()).isEqualTo("UPDATE monitor_probe_items (changed: label, <sensitive>)")
                .doesNotContain(SECRET).doesNotContain("relabelled").doesNotContain("secretValue");
        assertThat(writes.get(0).description()).isEqualTo("INSERT monitor_probe_items");
        assertThat(writes.get(2).description()).isEqualTo("DELETE monitor_probe_items");
        assertThat(events()).extracting(CrudEvent::seq).isSorted();
    }

    @Test
    @DisplayName("rolled-back transaction after a flush reports FAILURE and never SUCCESS")
    void rolledBackIsFailure() {
        enterRequest("POST", ROUTE_PATTERN);
        tx.executeWithoutResult(s -> {
            items.saveAndFlush(new ProbeItem(SECRET, "NEW"));
            s.setRollbackOnly();
        });

        List<CrudEvent> writes = events().stream().filter(e -> e.operation() == CrudOperation.CREATE).toList();
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).result()).isEqualTo(CrudResult.FAILURE);
        assertThat(events()).noneMatch(e -> e.operation() != CrudOperation.READ && e.result() == CrudResult.SUCCESS);
        assertThat(items.findAll()).isEmpty();
    }

    @Test
    @DisplayName("events of one transaction appear only after it completes")
    void eventsAreEmittedAtCompletion() {
        enterRequest("POST", ROUTE_PATTERN);
        tx.executeWithoutResult(s -> {
            items.saveAndFlush(new ProbeItem(SECRET, "NEW"));
            assertThat(events()).noneMatch(e -> e.operation() == CrudOperation.CREATE);
        });
        assertThat(events()).anyMatch(e -> e.operation() == CrudOperation.CREATE && e.result() == CrudResult.SUCCESS);
    }

    @Test
    @DisplayName("repository reads are reported as READ with a row count; save is not double counted")
    void readsAreReportedAndSaveIsNotDoubleCounted() {
        enterRequest("GET", ROUTE_PATTERN);
        items.save(new ProbeItem(SECRET, "NEW"));
        items.save(new ProbeItem("other", "NEW"));
        items.findByStatus("NEW");
        items.findFirstBySecretValue(SECRET);
        items.findFirstBySecretValue("nope");
        items.existsByStatus("NEW");
        items.countByStatus("NEW");

        List<CrudEvent> all = events();
        assertThat(all.stream().filter(e -> e.operation() == CrudOperation.CREATE)).hasSize(2);
        List<CrudEvent> reads = all.stream().filter(e -> e.operation() == CrudOperation.READ).toList();
        assertThat(reads).extracting(CrudEvent::rowCount).containsExactly(2, 1, 0, 1, 2);
        assertThat(reads).allSatisfy(e -> {
            assertThat(e.result()).isEqualTo(CrudResult.SUCCESS);
            assertThat(e.table()).isEqualTo("monitor_probe_items");
            assertThat(e.entityId()).isNull();
            assertThat(e.route()).isEqualTo("GET " + ROUTE_PATTERN);
        });
        assertThat(reads.get(0).description()).isEqualTo("SELECT monitor_probe_items via findByStatus");
    }

    @Test
    @DisplayName("a failing read is reported as FAILURE and the exception is rethrown unchanged")
    void failingReadIsReportedAndRethrown() {
        enterRequest("GET", ROUTE_PATTERN);
        // Spring Data's own exception translation (inside the repository proxy) wraps it; the aspect must not alter it.
        assertThatThrownBy(() -> items.findBroken())
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("boom")
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.operation()).isEqualTo(CrudOperation.READ);
            assertThat(e.result()).isEqualTo(CrudResult.FAILURE);
        });
    }

    @Test
    @DisplayName("@Modifying queries are UPDATE/DELETE with the affected row count")
    void modifyingQueries() {
        enterRequest("PUT", ROUTE_PATTERN);
        tx.executeWithoutResult(s -> {
            items.save(new ProbeItem(SECRET, "A"));
            items.save(new ProbeItem("other", "A"));
        });
        long afterSaves = recorder.latestSeq();
        tx.executeWithoutResult(s -> {
            assertThat(items.rewriteStatus("A", "B")).isEqualTo(2);
            assertThat(items.purgeByStatus("B")).isEqualTo(2);
        });

        List<CrudEvent> events = recorder.read(afterSaves, CrudEventRecorder.MAX_LIMIT).events();
        assertThat(events).extracting(CrudEvent::operation).containsExactly(CrudOperation.UPDATE, CrudOperation.DELETE);
        assertThat(events).extracting(CrudEvent::rowCount).containsExactly(2, 2);
        assertThat(events).extracting(CrudEvent::result).containsOnly(CrudResult.SUCCESS);
        assertThat(events.get(0).description()).isEqualTo("UPDATE monitor_probe_items via rewriteStatus");
        assertThat(events.get(1).description()).isEqualTo("DELETE monitor_probe_items via purgeByStatus");
    }

    @Test
    @DisplayName("a rolled-back @Modifying query is reported as FAILURE")
    void rolledBackModifyingIsFailure() {
        enterRequest("PUT", ROUTE_PATTERN);
        tx.executeWithoutResult(s -> items.save(new ProbeItem(SECRET, "A")));
        long afterSave = recorder.latestSeq();
        tx.executeWithoutResult(s -> {
            items.rewriteStatus("A", "B");
            s.setRollbackOnly();
        });
        assertThat(recorder.read(afterSave, CrudEventRecorder.MAX_LIMIT).events())
                .filteredOn(e -> e.operation() == CrudOperation.UPDATE)
                .singleElement().satisfies(e -> assertThat(e.result()).isEqualTo(CrudResult.FAILURE));
    }

    @Test
    @DisplayName("excluded tables are never recorded (writes or reads)")
    void excludedTablesAreNeverRecorded() {
        enterRequest("POST", ROUTE_PATTERN);
        ExcludedProbe probe = excluded.save(new ExcludedProbe("hash-123"));
        probe.setTokenHash("hash-456");
        tx.executeWithoutResult(s -> excluded.findById(probe.getId()).orElseThrow().setTokenHash("hash-789"));
        excluded.findByTokenHash("hash-789");
        excluded.findAll();
        excluded.deleteById(probe.getId());

        assertThat(events()).isEmpty();
        // Read the table names from the real entities so the constant cannot drift from the schema mapping.
        assertThat(CrudEventCapture.EXCLUDED_TABLES).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.of(RefreshToken.class, RevokedToken.class, PasswordResetToken.class,
                        UserTokenRevocation.class, StoredFile.class, AuditLog.class, EmailDeliveryLog.class)
                        .map(type -> type.getAnnotation(jakarta.persistence.Table.class).name())
                        .toList());
    }

    @Test
    @DisplayName("nothing is recorded outside an HTTP request (scheduler, async, startup)")
    void nothingOutsideARequest() throws Exception {
        items.save(new ProbeItem(SECRET, "NEW"));
        items.findByStatus("NEW");
        ExecutorService background = Executors.newSingleThreadExecutor();
        try {
            enterRequest("POST", ROUTE_PATTERN);
            // The request context is thread-bound, so work on another (async/scheduler-like) thread has none.
            background.submit(() -> {
                items.save(new ProbeItem(SECRET, "ASYNC"));
                items.findByStatus("ASYNC");
            }).get(10, TimeUnit.SECONDS);
        } finally {
            background.shutdownNow();
        }
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("requests without a matched route (filter phase) and monitor requests are not recorded")
    void filterPhaseAndMonitorRequestsAreIgnored() {
        enterRequest("GET", null);
        items.save(new ProbeItem(SECRET, "NEW"));
        items.findByStatus("NEW");
        assertThat(events()).isEmpty();

        enterRequest("GET", "/api/v1/monitor/events");
        items.save(new ProbeItem(SECRET, "NEW"));
        items.findByStatus("NEW");
        assertThat(events()).isEmpty();
    }

    @Test
    @DisplayName("no event field ever contains a stored value, parameter or the raw URL")
    void noValuesLeakIntoEvents() throws Exception {
        enterRequest("PATCH", ROUTE_PATTERN);
        ProbeItem saved = items.save(new ProbeItem(SECRET, "NEW"));
        tx.executeWithoutResult(s -> items.findById(saved.getId()).orElseThrow().setSecretValue(SECRET + "-v2"));
        items.findFirstBySecretValue(SECRET);
        tx.executeWithoutResult(s -> items.rewriteStatus("NEW", "OLD-STATUS"));
        items.deleteById(saved.getId());

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(events());
        assertThat(json).isNotEmpty()
                .doesNotContain(SECRET).doesNotContain("OLD-STATUS").doesNotContain("/raw/url")
                .doesNotContain("q=secret").doesNotContain("someone@example.test");
    }

    @Test
    @DisplayName("person-identifying tables never carry an entity id, and user_profiles reads carry no row count")
    void identifiersAndExistenceAreRedacted() {
        assertThat(CrudEventCapture.REDACTED_ID_TABLES)
                .containsExactlyInAnyOrder("user_profiles", "event_registrations", "qr_credentials");
        enterRequest("POST", ROUTE_PATTERN);
        RedactedProbe saved = redacted.save(new RedactedProbe("person"));
        tx.executeWithoutResult(s -> redacted.findById(saved.getId()).orElseThrow().setName("renamed"));
        redacted.findByName("person");
        redacted.existsByName("nobody");
        redacted.deleteById(saved.getId());

        List<CrudEvent> all = events();
        assertThat(all).extracting(CrudEvent::operation).contains(CrudOperation.CREATE, CrudOperation.UPDATE,
                CrudOperation.DELETE, CrudOperation.READ);
        assertThat(all).allSatisfy(e -> {
            assertThat(e.table()).isEqualTo("user_profiles");
            assertThat(e.entityId()).isNull();
            if (e.operation() == CrudOperation.READ) {
                assertThat(e.rowCount()).isNull();
            }
        });
        assertThat(all.stream().filter(e -> e.operation() == CrudOperation.UPDATE).findFirst().orElseThrow()
                .description()).isEqualTo("UPDATE user_profiles (changed: name)");
    }

    @Test
    @DisplayName("a transaction over the cap emits the first writes plus one summary event with the outcome")
    void oversizedTransactionGetsOneSummaryEvent() {
        enterRequest("POST", ROUTE_PATTERN);
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 8; i++) {
                items.saveAndFlush(new ProbeItem(SECRET, "NEW"));
            }
        });
        List<CrudEvent> writes = events();
        assertThat(writes).hasSize(6);
        assertThat(writes.subList(0, 5)).allSatisfy(e -> assertThat(e.table()).isEqualTo("monitor_probe_items"));
        CrudEvent summary = writes.get(5);
        assertThat(summary.table()).isEqualTo("(multiple)");
        assertThat(summary.operation()).isEqualTo(CrudOperation.CREATE);
        assertThat(summary.result()).isEqualTo(CrudResult.SUCCESS);
        assertThat(summary.description()).isEqualTo("3 more writes in this transaction omitted");
        assertThat(summary.entityId()).isNull();
        assertThat(summary.route()).isEqualTo("POST " + ROUTE_PATTERN);

        long mark = recorder.latestSeq();
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 6; i++) {
                items.saveAndFlush(new ProbeItem(SECRET, "NEW"));
            }
            s.setRollbackOnly();
        });
        List<CrudEvent> rolledBack = recorder.read(mark, CrudEventRecorder.MAX_LIMIT).events();
        assertThat(rolledBack).hasSize(6).allSatisfy(e -> assertThat(e.result()).isEqualTo(CrudResult.FAILURE));
        assertThat(rolledBack.get(5).description()).isEqualTo("1 more writes in this transaction omitted");
    }

    @Test
    @DisplayName("an event keeps the time the write was seen, not the commit time")
    void timestampIsOperationTime() {
        enterRequest("POST", ROUTE_PATTERN);
        java.time.Instant[] bounds = new java.time.Instant[2];
        tx.executeWithoutResult(s -> {
            bounds[0] = java.time.Instant.now();
            items.saveAndFlush(new ProbeItem(SECRET, "NEW"));
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            bounds[1] = java.time.Instant.now();
        });
        CrudEvent event = events().get(0);
        assertThat(event.timestamp()).isAfterOrEqualTo(bounds[0]).isBefore(bounds[1].minusMillis(150));
    }

    @Test
    @DisplayName("a rollback before any flush leaves no event (documented limit: UUID inserts are deferred to flush)")
    void rollbackBeforeFlushLeavesNoEvent() {
        enterRequest("POST", ROUTE_PATTERN);
        tx.executeWithoutResult(s -> {
            items.save(new ProbeItem(SECRET, "NEW"));
            s.setRollbackOnly();
        });
        assertThat(events()).noneMatch(e -> e.operation() == CrudOperation.CREATE);
    }

    // ---- Through DispatcherServlet: real route matching, no hand-set request attributes ----

    @org.springframework.web.bind.annotation.RestController
    static class ProbeController {

        private final ProbeItemRepository repository;
        private final TransactionTemplate transaction;

        ProbeController(ProbeItemRepository repository, TransactionTemplate transaction) {
            this.repository = repository;
            this.transaction = transaction;
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/probes/{label}")
        org.springframework.http.ResponseEntity<String> create(
                @org.springframework.web.bind.annotation.PathVariable String label) {
            repository.save(new ProbeItem("v", label));
            return org.springframework.http.ResponseEntity.ok("created");
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/probes/{label}/fail")
        org.springframework.http.ResponseEntity<String> createThenFail(
                @org.springframework.web.bind.annotation.PathVariable String label) {
            transaction.executeWithoutResult(s -> {
                repository.saveAndFlush(new ProbeItem("v", label));
                throw new IllegalStateException("handler failed after save");
            });
            return org.springframework.http.ResponseEntity.ok("unreachable");
        }

        @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
        org.springframework.http.ResponseEntity<String> handle() {
            return org.springframework.http.ResponseEntity.status(500).body("failed");
        }
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ProbeController(items, tx)).build();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    @Test
    @DisplayName("HTTP request through DispatcherServlet: event carries the real matched pattern, role and SUCCESS")
    void realRequestCarriesMatchedRoute() throws Exception {
        authenticate();
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/probes/literal-value-123?token=abc"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.operation()).isEqualTo(CrudOperation.CREATE);
            assertThat(e.result()).isEqualTo(CrudResult.SUCCESS);
            assertThat(e.route()).isEqualTo("POST /api/v1/probes/{label}");
            assertThat(e.actorRole()).isEqualTo("STAFF");
            assertThat(e.description()).doesNotContain("literal-value-123");
        });
    }

    @Test
    @DisplayName("HTTP request whose handler throws after a flushed save: FAILURE (rolled back), no SUCCESS")
    void realRequestThatFailsAfterSaveIsFailure() throws Exception {
        authenticate();
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/probes/another-literal/fail"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError());

        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.operation()).isEqualTo(CrudOperation.CREATE);
            assertThat(e.result()).isEqualTo(CrudResult.FAILURE);
            assertThat(e.route()).isEqualTo("POST /api/v1/probes/{label}/fail");
        });
        assertThat(items.findAll()).isEmpty();
    }
}
