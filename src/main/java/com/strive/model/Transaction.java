package com.strive.model;

import com.strive.util.CategoryRegistry;

import java.time.LocalDate;

/**
 * Immutable data record representing a single spending transaction
 * Transactions are stored as is in the sb with no transformation
 * - all aggregation and formatting is handled by the BLL layer
 *
 * @param id        unique primary key
 * @param amount    amount spend, in dollars
 * @param category  spending category, must a vlaue in {@link CategoryRegistry}
 * @param date      date the transaction occurred
 */

public record Transaction(int id, double amount, String category, LocalDate date) implements DBRecord {

}

