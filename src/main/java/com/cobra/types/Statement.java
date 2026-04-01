package com.cobra.types;

public class Statement {

	public enum QueryT {
		INSERT_Q,
		DELETE_Q,
		UPDATE_Q
	}

	public enum TableT {
		TRANSACTIONS_TB,
		LIMITS_TB
	}

	private final QueryT QueryType;
	private final TableT TableType;
	private int id;
	private double amount;
	private String category;
	private String date;

	private Statement(Builder builder) {
		this.QueryType = builder.QueryType;
		this.TableType = builder.TableType;
		this.id = builder.id;
		this.amount = builder.amount;
		this.category = builder.category;
		this.date = builder.date;
	}

	public static class Builder {
		private final QueryT QueryType;
		private final TableT TableType;
		private int id = -1;
		private double amount = -1;
		private String category = "";
		private String date = "";

		public Builder(QueryT qt, TableT tt) {
			this.QueryType = qt;
			this.TableType = tt;
		}

		public Builder id(int val) {
			id = val;
			return this;
		}

		public Builder amount(double val) {
			amount = val;
			return this;
		}

		public Builder category(String val) {
			category = val;
			return this;
		}

		public Builder date(String val) {
			date = val;
			return this;
		}

		public Statement build() {
			return new Statement(this);
		}
	}

	public QueryT getQueryType() {
		return QueryType;
	}

	public TableT getTableType() {
		return TableType;
	}

	public int getID() {
		return id;
	}

	public double getAmount() {
		return amount;
	}

	public String getCategory() {
		return category;
	}

	public String getDate() {
		return date;
	}
}
