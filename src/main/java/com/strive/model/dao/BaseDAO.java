package com.strive.model.dao;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Abstract base class for all Data Access Objects (DAOs) in CoBRA.
 *
 * Centralizes the two core database operations — write and query — so that
 * concrete DAOs ({@link LimitDAO}, {@link TransactionDAO}) only need to supply
 * the SQL string and a row-mapping lambda. Connection lifecycle (open, use,
 * close) is managed here via try-with-resources, keeping subclasses free of
 * boilerplate.
 *
 * All DAOs share a single SQLite database file identified by {@code dbUrl}.
 * Connections are created fresh for every operation (no connection pooling),
 * which is appropriate for a single-user desktop application.
 *
 * @see LimitDAO
 * @see TransactionDAO
 */

public abstract class BaseDAO {
    /** JDBC connection URL for the SQLite database file (e.g. {@code "jdbc:sqlite:/path/to/file.db"}). */
    protected final String dbUrl;

    /**
     * Constructs a BaseDAO wired to the given database URL.
     *
     * @param dbUrl the JDBC URL of the SQLite database; passed down from
     *              {@link com.strive.config.AppContext} at startup
     */
    protected BaseDAO(String dbUrl) { this.dbUrl = dbUrl; }

    /**
     * Strategy interface for converting a single {@link ResultSet} row into a
     * typed model object.
     *
     * Implementations are typically supplied as lambdas inline at the call
     * site in each concrete DAO. The mapper is called once per row inside
     * {@link #executeQuery}.
     *
     * @param <T> the model type produced by the mapping (e.g. {@code Transaction})
     */
    @FunctionalInterface
    protected interface RowMapper<T> {
        /**
         * Maps the current row of {@code rs} to a model object.
         * The cursor is already positioned on the row — do not call {@code rs.next()}.
         *
         * @param rs the result set, positioned on the current row
         * @return the mapped model object
         * @throws SQLException if any column access fails
         */
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Executes a SQL statement that modifies the database (INSERT, UPDATE, DELETE).
     *
     * Opens a fresh connection, executes the statement, then closes the
     * connection automatically via try-with-resources. Any {@link SQLException}
     * is printed to stderr but not re-thrown — callers should treat a silent
     * return as a potential failure if strict error handling is needed in future.
     *
     * @param sql the fully-formed SQL string to execute; must not be {@code null}
     */
    protected void executeWrite(String sql) {
        // Log every write so database activity is visible in the console during development
        System.out.println("[" + getClass().getSimpleName() + "] SQL: " + sql);

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** ISO-8601 date format used by all new records (e.g. {@code "2024-05-01"}). */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    /**
     * Legacy date format present in older database rows (e.g. {@code "05-01-2024"}).
     * Kept for backward compatibility; new writes always use {@link #ISO}.
     */
    private static final DateTimeFormatter LEGACY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /**
     * Parses a date string that may be in either ISO-8601 or legacy MM-dd-yyyy format.
     *
     * First attempts ISO parsing; if that throws a {@link DateTimeParseException}
     * it falls back to the legacy format. This handles rows written by older versions
     * of the app without requiring a database migration.
     *
     * @param raw the raw date string read from a database column
     * @return the parsed {@link LocalDate}
     * @throws DateTimeParseException if the string matches neither format
     */
    protected static LocalDate parseDate(String raw) {
        try { return LocalDate.parse(raw, ISO); } // fast path — all modern rows use ISO
        catch (DateTimeParseException e) {
            return LocalDate.parse(raw, LEGACY); // fallback for legacy MM-dd-yyyy rows
        }
    }

    /**
     * Executes a SQL SELECT query and maps each result row to a typed object.
     *
     * Opens a fresh connection, streams all rows through the supplied
     * {@link RowMapper}, collects the results into a list, then closes the
     * connection automatically. Returns an empty list (never {@code null}) if
     * no rows match or if a {@link SQLException} occurs.
     *
     * @param <T>    the model type produced by {@code mapper}
     * @param sql    the fully-formed SELECT statement to execute
     * @param mapper a {@link RowMapper} lambda that converts one row to a {@code T}
     * @return an {@link ArrayList} of mapped objects; empty if no rows matched
     */
    protected <T> ArrayList<T> executeQuery(String sql, RowMapper<T> mapper) {
        ArrayList<T> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            // Iterate over every row, mapping each one to a model object
            while (rs.next()) {
                results.add(mapper.map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results; // always a list — callers can safely call .isEmpty() or iterate
    }
}
