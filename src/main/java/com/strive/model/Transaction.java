package com.strive.model;

import com.strive.util.CategoryRegistry;

import java.time.LocalDate;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Immutable data record representing a single spending transaction entered
 * by the user.
 *
 * Transactions are stored as-is in the database with no transformation —
 * raw dollar amounts and category strings are persisted directly. All
 * aggregation, filtering, and formatting is delegated to the business logic
 * layer (BLL), keeping this record a pure data carrier.
 *
 * Like {@link SpendingLimit}, this is a Java {@code record}, so all fields
 * are final. To "edit" a transaction the session layer replaces the record
 * entirely via {@link com.strive.session.command.EditCommand}.
 *
 * @param id       unique integer primary key; matches the {@code id} column
 *                 in the {@code transactions} table; {@code 0} if not yet persisted
 * @param amount   the dollar amount spent; expected to be a positive value
 *                 (e.g. {@code 42.50})
 * @param category the spending category for this transaction (e.g. "Food",
 *                 "Transport"); must match a key registered in
 *                 {@link CategoryRegistry} so limit comparisons work correctly
 * @param date     the calendar date on which the transaction occurred;
 *                 used by the BLL to bucket spending by day or week
 *
 * @see com.strive.model.dao.TransactionDAO
 * @see com.strive.controller.TransactionController
 * @see com.strive.bll.SpendingCalculator
 */

public record Transaction(int id, // primary key — 0 if not yet persisted to the DB
                          double amount, // dollar amount spent; should always be > 0
                          String category, // category label; must exist in CategoryRegistry
                          LocalDate date) // date the transaction occurred; drives weekly bucketing
        implements DBRecord {
    // No additional methods needed — record automatically generates
    // accessors (id(), amount(), category(), date()),
    // equals(), hashCode(), and toString().
}

