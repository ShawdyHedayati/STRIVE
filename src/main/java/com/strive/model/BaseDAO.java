package com.strive.model;

import java.sql.*;

public abstract class BaseDAO {
    protected final String dbUrl;

    protected BaseDAO(String dbUrl) { this.dbUrl = dbUrl; }

    protected void executeWrite(String sql) {
        System.out.println("[" + getClass().getSimpleName() + "] SQL: " + sql);

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
