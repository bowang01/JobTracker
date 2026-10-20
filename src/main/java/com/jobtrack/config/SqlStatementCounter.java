package com.jobtrack.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the SQL statements Hibernate prepares on the current thread, so {@code RequestTimingFilter}
 * can report how many queries each request ran (a sudden jump points at an N+1 query).
 * Registered through {@code hibernate.session_factory.statement_inspector} in application.properties.
 * It never changes the SQL.
 */
public class SqlStatementCounter implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int count() {
        return COUNT.get()[0];
    }

    public static void clear() {
        COUNT.remove();
    }
}
