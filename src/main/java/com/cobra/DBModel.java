package com.cobra;

import com.cobra.types.*;

import java.util.ArrayList;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.io.File;

public class DBModel {
	String dburl = null;
	File topDir = null;
	File dbFile = null;

	// Generic mapping function to ingest SQL rows as ResultSet object
	@FunctionalInterface
	public interface RowMapper<T> {
		T map(ResultSet rs) throws SQLException;
	}

	// Initializes database file and file paths
	// TODO: Needs checks and error handling
	public void initDB() {
		topDir = new File(System.getProperty("user.dir"));
		dbFile = new File(topDir, "/src/main/resources/strive_test.db");
		dburl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
		// TODO: Ideally this isn't a hardcoded filepath
		// Should probably search through top of directory for .db file with correct
		// name and use its location
	}

	// Function to gather rows from SQL database file using RowMapper interface
	public <T> ArrayList<T> fetchRows(String query, RowMapper<T> mapper) {
		ArrayList<T> resultList = new ArrayList<>();
		try (var conn = DriverManager.getConnection(dburl)) {
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				resultList.add(mapper.map(rs));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return resultList;
	}

	// Transaction typed fetchRows using Lambda
	public ArrayList<Transaction> fetchTransactions() {
		String query = "SELECT * FROM transactions";
		return fetchRows(query, rs -> new Transaction(
				rs.getInt("id"),
				rs.getDouble("amount"),
				rs.getString("category"),
				rs.getString("date")));
	}

	// Goal typed fetchRows using Lambda
	public ArrayList<Goal> fetchGoals() {
		String query = "SELECT * FROM goals";
		return fetchRows(query, rs -> new Goal(
				rs.getInt("id"),
				rs.getDouble("amount"),
				rs.getString("category"),
				rs.getString("date")));
	}
}
