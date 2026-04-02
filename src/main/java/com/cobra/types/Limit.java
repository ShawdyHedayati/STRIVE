package com.cobra.types;

import com.cobra.types.Statement;

public record Limit(int id, double amount, String category, String date) implements DBRecord {
	public Limit(Statement s) {
		this(s.getID(), s.getAmount(), s.getCategory(), s.getDate());
	}

	public int getID() {
		return this.id;
	}
}
