package com.cobra.types;

public record Transaction(int id, double amount, String category, int date) {
	// TODO: Add input validation
}
