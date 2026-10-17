package com.thedavelopers.eventqr.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Covers the self-healing branch of {@link FreshDatabaseBootstrapCallback} that no other test reaches: core tables
 * gone while {@code flyway_schema_history} survives (dropped tables or a partial restore). The callback still rebuilds
 * the baseline there - that is why it probes the tables rather than the history - but it must say so at WARN so the
 * state stays observable. Also pins the two quiet paths: a genuinely fresh database, and an already-provisioned one.
 *
 * <p>The callback's JDBC traffic is faked at the {@link Connection} seam with Mockito, keyed by SQL text, so these
 * tests need no Docker and no real database.
 */
class FreshDatabaseBootstrapCallbackHistoryWarningTest {

    @Test
    void aNonEmptyHistoryWithoutCoreTablesWarnsAndStillAppliesTheBaseline() throws Exception {
        FakeDatabase database = new FakeDatabase()
                .withTable("public.flyway_schema_history")
                .withHistoryRows(true);

        List<ILoggingEvent> logs = bootstrap(database.connection());

        List<ILoggingEvent> warnings = logsAt(logs, Level.WARN);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getFormattedMessage()).contains("flyway_schema_history is not empty")
                .contains("V16 baseline");

        List<ILoggingEvent> infos = logsAt(logs, Level.INFO);
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getFormattedMessage()).contains("Empty database detected");

        assertThat(database.executed).hasSize(2);
        assertThat(database.executed.get(0)).contains("CREATE TABLE IF NOT EXISTS");
        assertThat(database.executed.get(1)).contains("ALTER TABLE transaction_rules");
    }

    @Test
    void aFreshDatabaseWithAnEmptyHistoryDoesNotWarn() throws Exception {
        FakeDatabase database = new FakeDatabase()
                .withTable("public.flyway_schema_history")
                .withHistoryRows(false);

        List<ILoggingEvent> logs = bootstrap(database.connection());

        assertThat(logsAt(logs, Level.WARN)).isEmpty();
        assertThat(logsAt(logs, Level.INFO)).hasSize(1);
        assertThat(database.executed).hasSize(2);
    }

    @Test
    void anExistingEventsTableShortCircuitsWithoutWarningOrBaseline() throws Exception {
        FakeDatabase database = new FakeDatabase().withTable("public.events");

        List<ILoggingEvent> logs = bootstrap(database.connection());

        assertThat(logsAt(logs, Level.WARN)).isEmpty();
        assertThat(logsAt(logs, Level.INFO)).isEmpty();
        assertThat(database.executed).isEmpty();
    }

    /**
     * The probes must be schema-qualified: the callback decides on {@code public.*} whatever the connection's
     * {@code current_schema()} is, so no probe may build a name from it.
     */
    @Test
    void probesAreQualifiedToPublicAndNeverUseCurrentSchema() throws Exception {
        FakeDatabase database = new FakeDatabase()
                .withTable("public.flyway_schema_history")
                .withHistoryRows(true);

        bootstrap(database.connection());

        assertThat(database.queried).isNotEmpty()
                .allSatisfy(sql -> assertThat(sql).doesNotContain("current_schema"));
        assertThat(database.queried.stream().filter(sql -> sql.contains("to_regclass")).toList())
                .containsExactly("select to_regclass('public.events')",
                        "select to_regclass('public.user_profiles')",
                        "select to_regclass('public.flyway_schema_history')");
    }

    /** Runs the real callback against a faked connection and returns everything it logged to its own logger. */
    private static List<ILoggingEvent> bootstrap(Connection connection) {
        Logger logger = (Logger) LoggerFactory.getLogger(FreshDatabaseBootstrapCallback.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new FreshDatabaseBootstrapCallback().handle(Event.BEFORE_MIGRATE, context);
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private static List<ILoggingEvent> logsAt(List<ILoggingEvent> events, Level level) {
        return events.stream().filter(event -> event.getLevel() == level).toList();
    }

    /**
     * In-memory stand-in for the handful of queries the callback issues, resolved by SQL text: a {@code to_regclass}
     * probe returns the table name when it exists (null otherwise), and the {@code exists(...)} history probe returns
     * the configured row flag. Tables are registered schema-qualified (e.g. {@code public.events}) to mirror the
     * callback's qualified probes. Baseline statements executed through it are recorded for assertions, and every
     * query is recorded so the probe SQL itself can be asserted on.
     */
    private static final class FakeDatabase {

        private final List<String> existingTables = new ArrayList<>();
        private final List<String> queried = new ArrayList<>();
        private final List<String> executed = new ArrayList<>();
        private boolean historyHasRows;

        FakeDatabase withTable(String qualifiedTable) {
            existingTables.add(qualifiedTable);
            return this;
        }

        FakeDatabase withHistoryRows(boolean rows) {
            this.historyHasRows = rows;
            return this;
        }

        Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            when(connection.createStatement()).thenAnswer(invocation -> statement());
            return connection;
        }

        private Statement statement() throws SQLException {
            Statement statement = mock(Statement.class);
            when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
                queried.add(invocation.getArgument(0));
                return resultSet(invocation.getArgument(0));
            });
            when(statement.execute(anyString())).thenAnswer(invocation -> {
                executed.add(invocation.getArgument(0));
                return false;
            });
            return statement;
        }

        private ResultSet resultSet(String sql) throws SQLException {
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true);
            if (sql.contains("exists(select 1 from public.flyway_schema_history)")) {
                when(resultSet.getBoolean(1)).thenReturn(historyHasRows);
            } else if (sql.contains("to_regclass")) {
                when(resultSet.getString(1)).thenReturn(probedTable(sql));
            }
            return resultSet;
        }

        private String probedTable(String sql) {
            for (String table : existingTables) {
                if (sql.contains("'" + table + "'")) {
                    return table;
                }
            }
            return null;
        }
    }
}
