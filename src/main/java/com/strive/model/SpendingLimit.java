package com.strive.model;

import java.time.LocalDate;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Immutable data record representing a user-defined weekly spending limit
 * for a single budget category.
 *
 * Each {@code SpendingLimit} caps how much the user intends to spend in
 * a given category during the current week. At the start of each new week,
 * {@link com.strive.controller.LimitController#weeklyReset()} archives the
 * old limits and creates fresh ones so the cycle restarts cleanly.
 *
 * Because this is a Java {@code record}, all fields are final and
 * set at construction time — there are no setters. To "edit" a limit,
 * the session layer replaces the record entirely via {@link com.strive.session.command.EditCommand}.
 *
 * @param id        unique integer primary key; matches the {@code id} column
 *                  in the {@code spending_limits} table
 * @param amount    the maximum amount (in dollars) the user intends to spend
 *                  in this category for the current week; must be &gt; 0
 * @param category  the budget category this limit applies to (e.g. "Food",
 *                  "Transport"); must match a key in {@link com.strive.util.CategoryRegistry}
 * @param createdAt the calendar date on which this limit record was created;
 *                  used by {@code weeklyReset()} to detect stale limits from
 *                  a prior week
 *
 * @see com.strive.model.dao.LimitDAO
 * @see com.strive.controller.LimitController
 */

public record SpendingLimit(int id, // primary key — 0 if not yet persisted to the DB
                            double amount, // weekly spending cap in dollars (e.g. 150.00)
                            String category, // budget category label (must exist in CategoryRegistry)
                            LocalDate createdAt) // creation date; drives weekly reset logic
        implements DBRecord {
    // No additional methods needed — record automatically generates
    // accessors (id(), amount(), category(), createdAt()),
    // equals(), hashCode(), and toString().
}
