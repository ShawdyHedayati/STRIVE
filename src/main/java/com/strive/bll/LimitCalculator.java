package com.strive.bll;

import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;
import com.strive.util.DateUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for all spending limit calculations used by the dash's
 * limit bar islands
 *
 * All calculations are scoped to the current week
 * Matching how limits are defined and reset
 * Methods are stateless and take their inputs from the session layer
 */

public class LimitCalculator {
    /**
     * Returns the total amount spend in a given category during the current week
     * This is the base figure used by all other methods in this class
     *
     * @param category the category to filter by, not case-sensitive
     * @param transactions the full in memory transaction list
     * @return totall weekly spending for the cat
     */
    public double spentForCategory(String category, List<Transaction> transactions) {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek();

        return transactions.stream()
                .filter(t -> t.category().equalsIgnoreCase(category))
                .filter(t -> !t.date().isBefore(startOfWeek))
                .mapToDouble(Transaction::amount)
                .sum();
    }

    /**
     * Returns the fill percentage for the limit bar (0-100)
     * Clamped to 100 so the bar never overflows its container so the bar
     * turns red instead when {@link #isOverLimit} returns true
     *
     * @param limit the spending limit to evaluate
     * @param transactions the full in memory transaction list
     * @return fill percentage, rounded to 2 decimal places, max 100
     */
    public double fillPercent(SpendingLimit limit, List<Transaction> transactions) {
        if (limit.amount() == 0) return 100.0; // avoid div by zero

        double spent = spentForCategory(limit.category(), transactions);
        return Math.min(100.0, Math.round((spent / limit.amount()) * 10000.0) / 100.0);
    }

    /**
     * Returns {@code true} if spending for this cat has exceeded the lim
     * Used by the UI to turn limit bar red
     *
     * @param limit the spending limit to evaluate
     * @param transactions the full in memory transaction list
     * @return true if over limit
     */
    public boolean isOverLimit(SpendingLimit limit, List<Transaction> transactions) {
        return spentForCategory(limit.category(), transactions) > limit.amount();
    }

    /**
     * Produces the full list of limit bar data for rendering all limit islands
     * Each entry contains everything the UI needs to render on limit bar
     *
     * @param limits all active spending limits
     * @param transactions the full in memory transaction list
     * @return list of limit bar data, records, in the same order as the input limits
     */
    public List<LimitBarData> limitBarData(List<SpendingLimit> limits, List<Transaction> transactions) {
        return limits.stream()
                .map(l -> new LimitBarData(
                        l.category(),
                        l.amount(),
                        spentForCategory(l.category(), transactions),
                        fillPercent(l, transactions),
                        isOverLimit(l, transactions)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Immutable data carrier for rendering a single limit bar in the UI
     *
     * @param category the spending category
     * @param limitAmount the user defined limit for this cat
     * @param spent total spent in this category this week
     * @param fillPercent bar fill percentage (0-100)
     * @param isOver true if spent exceeds limitAmount (bar should turn red)
     */
    public record LimitBarData(
            String category,
            double limitAmount,
            double spent,
            double fillPercent,
            boolean isOver
    ) {}
}
