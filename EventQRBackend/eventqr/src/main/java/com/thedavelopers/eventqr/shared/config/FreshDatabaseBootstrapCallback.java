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
 * is present the callback does nothing, so live environments migrate exactly as before.
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
            if (tableExists(connection, "events") || tableExists(connection, "user_profiles")) {
                return;
            }
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

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select to_regclass(current_schema() || '." + table + "')")) {
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
