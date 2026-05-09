package com.strive.model.dao;

import com.strive.model.Transaction;

import java.util.ArrayList;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Data Access Object (DAO) for {@link Transaction} records.
 *
 * Handles all direct SQL reads and writes against the {@code transactions}
 * table. This is the only class that issues SQL against that table —
 * all other layers interact with transactions through the session layer, which
 * calls this DAO exclusively when
 * {@link com.strive.session.SessionManager#flush()} is explicitly invoked.
 *
 * Write timing: Write methods ({@link #insert}, {@link #update},
 * {@link #delete}) are never triggered directly by user actions. Changes
 * accumulate in the session's in-memory command log first, allowing them to be
 * undone before they are committed to the database via {@code flush()}.
 *
 * Connection management and result-set iteration are inherited from
 * {@link BaseDAO}, keeping this class focused purely on
 * transaction-specific SQL.
 *
 * @see BaseDAO
 * @see com.strive.session.SessionManager#flush()
 */

public class TransactionDAO extends BaseDAO {
    /**
     * Constructs a {@code TransactionDAO} connected to the given SQLite database.
     *
     * @param dbUrl JDBC connection URL for the SQLite file
     *              (e.g. {@code "jdbc:sqlite:/path/to/strive.db"})
     */
    public TransactionDAO(String dbUrl) { super(dbUrl); }

    /**
     * Fetches every row from the {@code transactions} table and maps each one
     * to a {@link Transaction} record.
     *
     * Called during session initialization to hydrate the in-memory state
     * with all persisted transactions. The {@code date} column is parsed via
     * {@link BaseDAO#parseDate(String)} to handle both ISO-8601 and legacy
     * date formats transparently.
     *
     * @return an {@link ArrayList} of all {@link Transaction} records;
     *         empty if the table has no rows
     */
    public ArrayList<Transaction> fetchAll() {
        return executeQuery("SELECT * FROM transactions", rs -> new Transaction(
                rs.getInt("id"), // primary key
                rs.getDouble("amount"), // dollar amount spent
                rs.getString("category"), // budget category label
                parseDate(rs.getString("date")) // handles ISO and legacy date formats
        ));
    }

    /**
     * Inserts a new row into the {@code transactions} table for the given transaction.
     *
     * The {@code id} field of {@code t} must already be set to the intended
     * primary key value — ID assignment is managed by the session layer, not
     * by database auto-increment.
     *
     * @param t the {@link Transaction} to persist; must not be {@code null}
     */
    public void insert(Transaction t) {
        // Single-quoted string literals for category and date to satisfy SQLite syntax
        String sql = String.format(
                "INSERT INTO transactions (id, amount, category, date) VALUES (%d, %f, '%s', '%s')",
                t.id(), t.amount(), t.category(),t.date());
        executeWrite(sql);
    }

    /**
     * Updates the {@code amount}, {@code category}, and {@code date} columns of
     * an existing row, matched by the transaction's primary key ({@code id}).
     *
     * The {@code id} column is never modified — it is only used in the
     * {@code WHERE} clause to locate the target row.
     *
     * @param t the {@link Transaction} containing the updated values;
     *          its {@code id()} must match an existing row or the update is a no-op
     */
    public void update(Transaction t) {
        // Only mutable fields (amount, category, date) are updated; id is the key
        String sql = String.format(
                "UPDATE transactions SET amount=%f, category='%s', date='%s' WHERE id=%d",
                t.amount(), t.category(), t.date(), t.id());
        executeWrite(sql);
    }

    /**
     * Deletes the row with the given primary key from the {@code transactions} table.
     *
     * If no row with {@code id} exists, the operation is a silent no-op
     * (SQLite does not error on a DELETE that matches zero rows).
     *
     * @param id the primary key of the {@link Transaction} to remove
     */
    public void delete(int id) {
        // Simple single-column WHERE clause — no risk of ambiguity
        executeWrite("DELETE FROM transactions WHERE id = " + id);
    }
}
