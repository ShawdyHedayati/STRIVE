package com.strive.model.dao;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

public abstract class BaseDAO {
    protected final String dbUrl;

    protected BaseDAO(String dbUrl) { this.dbUrl = dbUrl; }

    @FunctionalInterface
    protected interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    protected void executeWrite(String sql) {
        System.out.println("[" + getClass().getSimpleName() + "] SQL: " + sql);

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LEGACY = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    protected static LocalDate parseDate(String raw) {
        try { return LocalDate.parse(raw, ISO); }
        catch (DateTimeParseException e) { return LocalDate.parse(raw, LEGACY); }
    }

    protected <T> ArrayList<T> executeQuery(String sql, RowMapper<T> mapper) {
        ArrayList<T> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapper.map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }
}
