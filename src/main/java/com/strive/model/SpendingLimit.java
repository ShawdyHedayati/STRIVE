package com.strive.model;

import java.time.LocalDate;

/**
 * Immutable data record representing a user defined weekly spending limit
 * for a single category. Limits are reset at the beginning of ea week by
 * {@link com.strive.controller.LimitController#weeklyReset()}
 *
 * @param id        unique primary key
 * @param amount    the max amount the user intends to spend this week
 * @param category  the category this limit applies to
 * @param createdAt the date this limit was created
 */

public record SpendingLimit(int id, double amount, String category, LocalDate createdAt) implements DBRecord {
}
