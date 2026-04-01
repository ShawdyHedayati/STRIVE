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

	private static DBModel instance;

	private DBModel() {
	}

	// Create Model if no Model exists, otherwise do nothing
	public static DBModel getInstance() {
		if (instance == null)
			instance = new DBModel();
		else
			System.out.println("Model already exists...");
		initDB();
		return instance;
	}

	// Generic mapping function to ingest SQL rows as ResultSet object
	@FunctionalInterface
	public interface RowMapper<T> {
		T map(ResultSet rs) throws SQLException;
	}

	// Initializes database file and file paths
	// TODO: This should create file if not exists otherwise find it at correct
	// location -> "src/main/resources/DB_FILENAME.db"
	public void initDB() {
		topDir = new File(System.getProperty("user.dir"));
		dbFile = new File(topDir, "/src/main/resources/strive_test.db");
		dburl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
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

	// Limit typed fetchRows using Lambda
	public ArrayList<Limit> fetchLimits() {
		String query = "SELECT * FROM goals";
		return fetchRows(query, rs -> new Limit(
				rs.getInt("id"),
				rs.getDouble("amount"),
				rs.getString("category"),
				rs.getString("date")));
	}

	// public void injestActions(ArrayList<Action> actions) {
	// TODO: Take list of actions and convert them into SQL queries
	// }

	public void createQuery(String query) {
		// TODO: SQL add query
	}

	public void updateQuery(String query) {
		// TODO: SQL update query
	}

	public void destroyQuery(String query) {
		// TODO: SQL destroy query
	}
}
