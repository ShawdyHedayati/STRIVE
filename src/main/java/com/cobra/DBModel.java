package com.cobra;

import com.cobra.types.*;

import java.util.ArrayList;
import java.util.function.Function;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.io.File;

public class DBModel {
	String dburl = null;
	File topDir = null;
	File dbFile = null;

	private static DBModel instance;
	public DBCache cache;

	public class DBCache {
		private ArrayList<Transaction> transactions;
		private ArrayList<Limit> limits;

		public DBCache() {
			transactions = fetchTransactions();
			limits = fetchLimits();
		}

		public ArrayList<Transaction> getTransactions() {
			return transactions;
		}

		public ArrayList<Limit> getLimits() {
			return limits;
		}

		public <T extends DBRecord> void executeStatement(
				Statement s,
				ArrayList<T> list,
				Function<Statement, T> factory) {
			switch (s.getQueryType()) {
				case INSERT_Q -> {
					T newObj = factory.apply(s);
					list.add(newObj);
				}
				case DELETE_Q -> {
					list.removeIf(obj -> obj.getID() == s.getID());
				}
				case UPDATE_Q -> {
					list.removeIf(obj -> obj.getID() == s.getID());
					T newObj = factory.apply(s);
					list.add(newObj);
				}
			}
		}

		public void processStatement(Statement s) {
			switch (s.getTableType()) {
				case TRANSACTIONS_TB ->
					executeStatement(s, transactions, Transaction::new);
				case LIMITS_TB ->
					executeStatement(s, limits, Limit::new);
			}
		}
	}

	private DBModel() {
	}

	// Initializes database file and file paths
	// TODO: This should create file if not exists otherwise find it at correct
	// location -> "src/main/resources/DB_FILENAME.db"
	public void initDB() {
		topDir = new File(System.getProperty("user.dir"));
		dbFile = new File(topDir, "/src/main/resources/strive_test.db");
		dburl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
		cache = new DBCache();
	}

	// Create Model if no Model exists, otherwise do nothing
	public static DBModel getInstance() {
		if (instance == null) {
			instance = new DBModel();
			instance.initDB();
		} else
			System.out.println("Model already exists...");
		return instance;
	}

	// Generic mapping function to ingest SQL rows as ResultSet object
	@FunctionalInterface
	public interface RowMapper<T> {
		T map(ResultSet rs) throws SQLException;
	}

	// Function to gather rows from SQL database file using RowMapper interface
	public <T> ArrayList<T> fetchRows(String query, RowMapper<T> mapper) {
		ArrayList<T> resultList = new ArrayList<>();
		try (var conn = DriverManager.getConnection(dburl)) {
			java.sql.Statement stmt = conn.createStatement();
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

	public void injestActions() {
		// TODO: Take list of actions and convert them into SQL queries
	}

	public void insertQuery(String query) {
		// TODO: SQL add query
	}

	public void updateQuery(String query) {
		// TODO: SQL update query
	}

	public void destroyQuery(String query) {
		// TODO: SQL destroy query
	}
}
