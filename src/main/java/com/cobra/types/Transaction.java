package com.cobra.types;

import com.cobra.types.Statement;

public record Transaction(int id, double amount, String category, String date) implements DBRecord {
	public Transaction(Statement s) {
		this(s.getID(), s.getAmount(), s.getCategory(), s.getDate());
	}

	public int getID() {
		return this.id;
	}
}
