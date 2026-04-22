package com.strive.model;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    /**
     * Fetches all spending limit rows from the db
     *
     * @return list of all spending limits, or empty list if none exists
     */
    public ArrayList<SpendingLimit> fetchAll() {
        // table is named 'goals' in the database schema
        String query = "SELECT * FROM goals";
        ArrayList<SpendingLimit> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                results.add(new SpendingLimit(
                        rs.getInt("id"),
                        rs.getDouble("amount"),
                        rs.getString("category"),
                        parseDate(rs.getString("date"))
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
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
     * Tries ISO format first (yyyy-MM-dd), falls back to legacy MM-dd-yyyy
     * This handles existing DB data while all new writes use ISO going
     * forward
     */
    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw, ISO);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(raw, LEGACY);
        }
    }

    /**
     * Deletes a spending limit row by its primary key.
     *
     * @param id  the ID of the spending limit to delete
     */
    public void delete(int id) { executeWrite("DELETE FROM goals WHERE id = " + id); }
}
