package com.thedavelopers.eventqr.features.monitor.support;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/** A DataSource that can never hand out a connection: proves code under test does not touch the database. */
public final class PoisonedDataSource implements DataSource {

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
