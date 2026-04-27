package com.strive.model.dao;

import com.strive.model.SpendingLimit;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 * Data Access Object for the {@code goals} table
 * Handles all direct SQL reads and writes for {@link SpendingLimit} records
 *
 * NOTE: underlying table is name {@code goals} to match the original schema. All Java facing code uses {@code SpendingLimit} as the type name.
 *
 * Like {@link TransactionDAO}, writes are only issued when
 * {@link com.strive.session.SessionManager#flush()} is called explicitly and never on every user action
 */

public class LimitDAO extends BaseDAO {
    // all new writes use ISO format
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    // handles existing db data stored in MM-dd-yyyy
    private static final DateTimeFormatter LEGACY = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    /**
     * Creates a new LimitDAO connected to given SQLite db URL
     *
     * @param dbUrl JDBC connection URL {@code jbdc:sqlite:/path/to/file.db}
     */
    public LimitDAO(String dbUrl) { super(dbUrl); }

    public ArrayList<SpendingLimit> fetchAll() {
        return executeQuery("SELECT * FROM goals", rs -> new SpendingLimit(
                rs.getInt("id"),
                rs.getDouble("amount"),
                rs.getString("category"),
                parseDate(rs.getString("date"))
        ));
    }

    /**
     * Insert a new spending limit row into db
     *
     * @param l the spending limit to insert
     */
    public void insert(SpendingLimit l) {
        String sql = String.format(
                "INSERT INTO goals (id, amount, category, date) VALUES (%d, %f, '%s', '%s')",
                l.id(), l.amount(), l.category(), l.createdAt());
        executeWrite(sql);
    }

    /**
     * Update an existing spending limit row, matched by ID
     *
     * @param l the spending limit with updated value
     */
    public void update(SpendingLimit l) {
        String sql = String.format(
                "UPDATE goals SET amount=%f, category='%s', date='%s' WHERE id=%d",
                l.amount(), l.category(), l.createdAt(), l.id());
        executeWrite(sql);
    }

    /**
     * Deletes a spending limit row by its primary key.
     *
     * @param id  the ID of the spending limit to delete
     */
    public void delete(int id) { executeWrite("DELETE FROM goals WHERE id = " + id); }
}
