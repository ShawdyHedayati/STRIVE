package com.cobra.types;

enum QueryT {
	INSERT,
	DELETE,
	UPDATE
}

enum TableT {
	TRANSACTIONS,
	LIMITS
}

public class Statement {
	private final QueryT QueryType;
	private final TableT TableType;
	private int id;
	private double amount;
	private String category;
	private int date;

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
		private int date = -1;

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

		public Builder date(int val) {
			date = val;
			return this;
		}

		public Statement build() {
			return new Statement(this);
		}
	}
}
