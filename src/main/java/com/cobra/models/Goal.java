package com.cobra.models;

public record Goal(int id, double amount, String category, int date) {
	public double getProgress() {
		// TODO: Implement this function as a call to DB or parse cache
		return amount;
	}
}
