package com.thedavelopers.eventqr.shared.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Lets Flyway build the schema on a completely empty database.
 *
 * <p>Migrations V1-V15 were written as incremental changes against tables that Hibernate had
 * already created, and the full baseline only arrives later in V16. On an empty database V2
 * therefore fails ("relation events does not exist"). Before the first migration runs, this
 * callback applies the V16 baseline DDL (all {@code CREATE TABLE IF NOT EXISTS}) when neither
 * core table exists, so V1-V15 then run against the tables they expect.
 *
 * <p>It never runs against an existing database: if {@code events} or {@code user_profiles}
 * is present the callback does nothing, so live environments migrate exactly as before. The
 * predicate deliberately probes those core tables rather than {@code flyway_schema_history}:
 * a database whose tables were dropped while the history survived is rebuilt from the baseline
 * here, whereas a history check would leave it to fail at V2 again. That state is logged as a
 * warning rather than acted on.
 *
 * <p>Every probe names the {@code public} schema explicitly instead of relying on
 * {@code current_schema()}: the callback must decide on the same tables the migrations create,
 * whatever the connection's search_path happens to be.
 */
public class FreshDatabaseBootstrapCallback implements Callback {

    private static final Logger log = LoggerFactory.getLogger(FreshDatabaseBootstrapCallback.class);

    static final String BASELINE_RESOURCE = "db/migration/V16__baseline_schema.sql";

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public String getCallbackName() {
        return "fresh-database-bootstrap";
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        try {
            if (tableExists(connection, "public.events") || tableExists(connection, "public.user_profiles")) {
                return;
            }
            warnIfHistoryExistsWithoutTables(connection);
            log.info("Empty database detected: applying {} before migrations", BASELINE_RESOURCE);
            try (Statement statement = connection.createStatement()) {
                statement.execute(readBaseline());
                // Column that live databases still carry from a removed Hibernate field and
                // that V8 writes to. V16 omits it, so add it here to match production.
                statement.execute("ALTER TABLE transaction_rules "
                        + "ADD COLUMN IF NOT EXISTS rule_config jsonb NOT NULL DEFAULT '{}'::jsonb");
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Unable to bootstrap the schema on an empty database", exception);
        }
    }

    /**
     * The core tables are gone but Flyway has run here before (dropped tables, a partial restore, or a manually
     * cleaned database). The callback still rebuilds the baseline - that is the point of probing the tables rather
     * than the history - but the state is worth saying out loud. Never fatal: a failed probe only logs.
     */
    private static void warnIfHistoryExistsWithoutTables(Connection connection) {
        try {
            if (!tableExists(connection, "public.flyway_schema_history") || !migrationHistoryHasRows(connection)) {
                return;
            }
            log.warn("flyway_schema_history is not empty but the baseline tables are missing; rebuilding the "
                    + "schema from the V16 baseline. If this database was not intentionally emptied, restore a "
                    + "backup instead of continuing.");
        } catch (SQLException exception) {
            log.debug("Could not inspect flyway_schema_history before bootstrapping", exception);
        }
    }

    private static boolean migrationHistoryHasRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select exists(select 1 from public.flyway_schema_history)")) {
            return result.next() && result.getBoolean(1);
        }
    }

    /**
     * True when {@code qualifiedName} (schema-qualified, e.g. {@code public.events}) resolves to a relation. The name
     * is always qualified by the caller so the probe never depends on the connection's {@code current_schema()}, which
     * may be a schema other than the one the migrations populate.
     */
    private static boolean tableExists(Connection connection, String qualifiedName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select to_regclass('" + qualifiedName + "')")) {
            return result.next() && result.getString(1) != null;
        }
    }

    /** V16 wraps itself in BEGIN/COMMIT; Flyway already owns the transaction here. */
    private static String readBaseline() throws IOException {
        try (InputStream in = new ClassPathResource(BASELINE_RESOURCE).getInputStream()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return sql.replaceAll("(?m)^\\s*(BEGIN|COMMIT)\\s*;\\s*$", "");
        }
    }
}
