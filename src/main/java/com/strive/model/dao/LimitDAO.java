package com.strive.model.dao;

import com.strive.model.SpendingLimit;

import java.util.ArrayList;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Data Access Object (DAO) for {@link SpendingLimit} records.
 *
 * Handles all direct SQL reads and writes against the {@code goals} table.
 * Each public method corresponds to one CRUD operation; all actual connection
 * management and result-set iteration is inherited from {@link BaseDAO}.
 *
 * Table name note: The underlying SQLite table is named {@code goals}
 * to match the original schema. All Java-facing code uses {@link SpendingLimit}
 * as the type name — the mismatch is intentional and confined to the SQL strings
 * in this class.
 *
 * Write timing: Like {@link TransactionDAO}, write methods
 * ({@link #insert}, {@link #update}, {@link #delete}) are only called when
 * {@link com.strive.session.SessionManager#flush()} is invoked explicitly.
 * They are never triggered directly by user actions, ensuring that in-session
 * changes can be undone before they reach the database.
 *
 * @see BaseDAO
 * @see com.strive.session.SessionManager#flush()
 */

public class LimitDAO extends BaseDAO {
    /**
     * Constructs a {@code LimitDAO} connected to the given SQLite database.
     *
     * @param dbUrl JDBC connection URL for the SQLite file
     *              (e.g. {@code "jdbc:sqlite:/path/to/strive.db"});
     *              note the original Javadoc had a typo ("jbdc") — the correct
     *              scheme is {@code jdbc}
     */
    public LimitDAO(String dbUrl) { super(dbUrl); }

    /**
     * Fetches every row from the {@code goals} table and maps each one to a
     * {@link SpendingLimit} record.
     *
     * Called during session initialization to hydrate the in-memory state
     * with the persisted limits. The {@code date} column is parsed via
     * {@link BaseDAO#parseDate(String)} to handle both ISO-8601 and legacy formats.
     *
     * @return an {@link ArrayList} of all {@link SpendingLimit} records;
     *         empty if the table has no rows
     */
    public ArrayList<SpendingLimit> fetchAll() {
        return executeQuery("SELECT * FROM goals", rs -> new SpendingLimit(
                rs.getInt("id"), // primary key
                rs.getDouble("amount"), // weekly spending cap in dollars
                rs.getString("category"), // budget category label
                parseDate(rs.getString("date")) // handles ISO and legacy date formats
        ));
    }

    /**
     * Inserts a new row into the {@code goals} table for the given limit.
     *
     * The {@code id} field of {@code l} must already be set to the intended
     * primary key value before calling this method — no auto-increment is used
     * here; ID assignment is managed by the session layer.
     *
     * @param l the {@link SpendingLimit} to persist; must not be {@code null}
     */
    public void insert(SpendingLimit l) {
        // Build a parameterized-style INSERT using String.format;
        // category is single-quoted to satisfy SQLite string literal syntax
        String sql = String.format(
                "INSERT INTO goals (id, amount, category, date) VALUES (%d, %f, '%s', '%s')",
                l.id(), l.amount(), l.category(), l.createdAt());
        executeWrite(sql);
    }

    /**
     * Updates the {@code amount}, {@code category}, and {@code date} columns of
     * an existing row, matched by the limit's primary key ({@code id}).
     *
     * The {@code id} column itself is never updated — it is only used in the
     * {@code WHERE} clause to locate the target row.
     *
     * @param l the {@link SpendingLimit} containing the updated values;
     *          its {@code id()} must match an existing row or the update is a no-op
     */
    public void update(SpendingLimit l) {
        // Only mutable fields (amount, category, date) are updated; id is the key
        String sql = String.format(
                "UPDATE goals SET amount=%f, category='%s', date='%s' WHERE id=%d",
                l.amount(), l.category(), l.createdAt(), l.id());
        executeWrite(sql);
    }

    /**
     * Deletes the row with the given primary key from the {@code goals} table.
     *
     * If no row with {@code id} exists, the operation is a silent no-op
     * (SQLite does not error on a DELETE that matches zero rows).
     *
     * @param id the primary key of the {@link SpendingLimit} to remove
     */
    public void delete(int id) {
        // Simple single-column WHERE clause — no risk of ambiguity
        executeWrite("DELETE FROM goals WHERE id = " + id);
    }
}
