package com.cobra.models;

// public class Transaction {
// 	private int id;
// 	private double amount;
// 	private String category;
// 	private int date;
//
// 	public int getId() {
// 		return id;
// 	}
//
// 	public double getAmount() {
// 		return amount;
// 	}
//
// 	public String getCategory() {
// 		return category;
// 	}
//
// 	public int getDate() {
// 		return date;
// 	}
//
// 	public void setId(int inputId) {
// 		id = inputId;
// 	}
//
// 	public void setAmount(double inputAmount) {
// 		amount = inputAmount;
// 	}
//
// 	public void setCategory(String inputCategory) {
// 		category = inputCategory;
// 	}
//
// 	public void setDate(int inputDate) {
// 		date = inputDate;
// 	}
// }

public record Transaction(int id, double amount, String category, String date) {
	// TODO: Add input validation
}
