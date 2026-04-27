package com.strive.model;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

/**
 * Data Access Object for the {@code transactions} table
 * Handles all direct SQL reads and writes for {@link Transaction} records
 *
 * This is the only class the issues SQL against the transactions table
 * All other layers interact with transactions throught the session layer,
 * which calls this DAO only when {@link com.strive.session.SessionManager#flush()}
 * is explicitly invoked.
 */

public class TransactionDAO extends BaseDAO {
    // all new writes use ISO format
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    // handles existing db data stored in MM-dd-yyyy
    private static final DateTimeFormatter LEGACY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /**
     * Creates a new TransactionDAO connected to the given SQLite db URL
     *
     * @param dbUrl JDBC connection URL (e.g. {@code jbdc:sqlite:/path/to/file.db})
     */
    public TransactionDAO(String dbUrl) { super(dbUrl); }

    public ArrayList<Transaction> fetchAll() {
        return executeQuery("SELECT * FROM transactions", rs -> new Transaction(
                rs.getInt("id"),
                rs.getDouble("amount"),
                rs.getString("category"),
                parseDate(rs.getString("date"))
        ));
    }

    /**
     * Inserts a new transaction row into the db
     *
     * @param t the transaction to insert
     */
    public void insert(Transaction t) {
        String sql = String.format(
                "INSERT INTO transactions (id, amount, category, date) VALUES (%d, %f, '%s', '%s')",
                t.id(), t.amount(), t.category(),t.date());
        executeWrite(sql);
    }

    /**
     * Updates an existing transaction row, matched by ID
     *
     * @param t the transaction with updated vals
     */
    public void update(Transaction t) {
        String sql = String.format(
                "UPDATE transactions SET amount=%f, category='%s', date='%s' WHERE id=%d",
                t.amount(), t.category(), t.date(), t.id());
        executeWrite(sql);
    }

    /**
     * Deletes a transaction row by its primary ley
     *
     * @param id the ID of the transaction to delete
     */
    public void delete(int id) { executeWrite("DELETE FROM transactions WHERE id = " + id); }
}
