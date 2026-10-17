package com.thedavelopers.eventqr.shared.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression pin for the Render health-probe fix: {@code /actuator/health/readiness} must answer
 * from in-process state only and must NEVER touch the database, while the aggregate
 * {@code /actuator/health} still reports the database so humans/other probes see the truth.
 *
 * <p>The whole point of the fix is that a DB-backed readiness indicator costs a full Hikari
 * connection-timeout (5 s) whenever the pool is exhausted or the database is unreachable — which
 * is the entire Render probe budget, so a missed probe restarts a healthy instance. This test
 * boots the real application context under the {@code prod} profile (so it exercises the actual
 * {@code management.endpoint.health.group.readiness.include} settings in
 * {@code application-prod.properties}, not a copy) with a DataSource that can never open a
 * connection. If the readiness group ever gains the {@code db} indicator, readiness drops to 503
 * and this test fails.
 *
 * <p>Caveat, kept explicit on purpose: an EMPTY or mis-typed readiness group resolves to
 * {@code UNKNOWN} -> HTTP 200, i.e. the pin FAILS OPEN at the status-code level alone. That is
 * why the assertions below also inspect the readiness group's members (see
 * {@code show-details=always} override) and would catch a typo by finding no {@code readinessState}
 * component.
 *
 * <p>No business code, config value or migration is touched — this only adds a test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // Neutralise every startup-time database dependency so the context can boot with a
                // poisoned DataSource: no migrations, no Hibernate schema validation/DDL, and no
                // JDBC metadata bootstrapping (the dialect is supplied explicitly instead).
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                // Test-only: expose health component details so the readiness group's MEMBERS can be
                // asserted. Production keeps show-details=when-authorized (unchanged).
                "management.endpoint.health.show-details=always",
                // Required, secret-backed properties that have no defaults; dummy test values only.
                "jwt.secret=testtesttesttesttesttesttesttesttesttesttesttest",
                "brevo.api.key=test-api-key-not-real",
                "brevo.sender.email=noreply@example.test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ReadinessProbeNeverTouchesDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("readiness probe stays UP/200 with a database that can never connect")
    void readinessProbeIsUpWhenDatabaseIsUnreachable() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // The group must contain the in-process readiness state...
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                // ...and must never contain the database indicator.
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    @DisplayName("aggregate health still reports the database DOWN/503")
    void aggregateHealthIsDownWhenDatabaseIsUnreachable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }

    /**
     * A DataSource that can never hand out a connection, with no startup-time cost: it is only
     * observed by the health indicator (Hibernate/Flyway are disabled for this test), so the
     * context boots and the verification of the readiness group is what is exercised.
     */
    @TestConfiguration
    static class PoisonedDataSourceConfiguration {

        @Bean
        @Primary
        DataSource dataSource() {
            return new PoisonedDataSource();
        }
    }

    /** Minimal {@link DataSource} whose every connection attempt fails, as an unreachable DB would. */
    private static final class PoisonedDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("poisoned DataSource: database deliberately unreachable for this test");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("poisoned DataSource: database deliberately unreachable for this test");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
