package com.cobra.types;

public record Transaction(int id, double amount, String category, String date) {
	// TODO: Add input validation
}
