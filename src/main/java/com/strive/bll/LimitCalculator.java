package com.strive.bll;

import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;
import com.strive.util.DateUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Business logic for all spending limit calculations displayed on the
 * dashboard's limit bar islands.
 *
 * All calculations are scoped to the current week (Monday–Sunday),
 * matching the period over which limits are defined and reset by
 * {@link com.strive.controller.LimitController#weeklyReset()}.
 *
 * Like {@link SpendingCalculator}, all methods are stateless —
 * they accept their inputs from the session layer and return derived values
 * without modifying any state or touching the database. This makes every
 * method independently testable with a plain list of transactions.
 *
 * @see SpendingCalculator
 * @see ChartCalculator
 * @see com.strive.ui.DashboardView
 */

public class LimitCalculator {
    /**
     * Returns the total amount spent in a given category during the current week.
     *
     * This is the base figure used by all other methods in this class.
     * The category comparison is case-insensitive, so {@code "food"} and
     * {@code "Food"} are treated as the same category.
     *
     * @param category     the category label to filter by; case-insensitive;
     *                     must not be {@code null}
     * @param transactions the full in-memory transaction list from the session;
     *                     must not be {@code null}
     * @return the total dollars spent in {@code category} this week;
     *         {@code 0.0} if no matching transactions exist
     */
    public double spentForCategory(String category, List<Transaction> transactions) {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek(); // Monday of the current week

        return transactions.stream()
                .filter(t -> t.category().equalsIgnoreCase(category)) // case-insensitive match
                .filter(t -> !t.date().isBefore(startOfWeek)) // current week only
                .mapToDouble(Transaction::amount)
                .sum(); // returns 0.0 if the stream is empty
    }

    /**
     * Returns the fill percentage for a limit's progress bar (0–100).
     *
     * The value is clamped to {@code 100.0} so the bar never visually
     * overflows its container. When spending exceeds the limit, the bar stays
     * full and the UI instead uses {@link #isOverLimit} to turn it red.
     *
     * Uses the same two-decimal rounding idiom as {@link SpendingCalculator#pieChartData}:
     * {@code Math.round(value * 10000.0) / 100.0}.
     *
     * @param limit        the spending limit to evaluate; must not be {@code null}
     * @param transactions the full in-memory transaction list; must not be {@code null}
     * @return the bar fill percentage, rounded to 2 decimal places, clamped to
     *         {@code [0.0, 100.0]}; returns {@code 100.0} if the limit amount is zero
     */
    public double fillPercent(SpendingLimit limit, List<Transaction> transactions) {
        // Guard: a zero limit would cause division by zero; treat it as fully spent
        if (limit.amount() == 0) return 100.0; // avoid div by zero

        double spent = spentForCategory(limit.category(), transactions);

        // Round to 2 decimal places, then clamp to 100 so the bar never overflows
        return Math.min(100.0, Math.round((spent / limit.amount()) * 10000.0) / 100.0);
    }

    /**
     * Returns {@code true} if spending for this category has met or exceeded
     * its weekly limit.
     *
     * Used by the UI to switch the limit bar's colour to red. Note that the
     * threshold is inclusive ({@code >=}), so reaching the limit exactly counts
     * as over-limit.
     *
     * @param limit        the spending limit to evaluate; must not be {@code null}
     * @param transactions the full in-memory transaction list; must not be {@code null}
     * @return {@code true} if {@code spentForCategory >= limit.amount()}
     */
    public boolean isOverLimit(SpendingLimit limit, List<Transaction> transactions) {
        // >= means "at or over" — hitting the limit exactly triggers the red bar
        return spentForCategory(limit.category(), transactions) >= limit.amount();
    }

    /**
     * Produces the full list of {@link LimitBarData} records needed to render
     * all limit bar islands on the dashboard.
     *
     * Each entry bundles everything the UI needs for one bar: the category
     * label, the limit amount, the current spend, the fill percentage, and the
     * over-limit flag. The output list preserves the same order as the input
     * {@code limits} list.
     *
     * @param limits       all active {@link SpendingLimit}s for the current week;
     *                     must not be {@code null}
     * @param transactions the full in-memory transaction list; must not be {@code null}
     * @return a mutable list of {@link LimitBarData}, one per limit, in input order;
     *         empty if {@code limits} is empty
     */
    public List<LimitBarData> limitBarData(List<SpendingLimit> limits, List<Transaction> transactions) {
        return limits.stream()
                .map(l -> new LimitBarData(
                        l.category(), // category display name
                        l.amount(), // user-defined weekly cap
                        spentForCategory(l.category(), transactions), // actual spend this week
                        fillPercent(l, transactions), // bar fill % (0–100)
                        isOverLimit(l, transactions) // true → bar turns red
                ))
                .collect(Collectors.toList());
    }

    /**
     * Immutable data carrier for rendering a single limit bar island in the UI.
     *
     * Produced exclusively by {@link LimitCalculator#limitBarData} and
     * consumed by {@link com.strive.ui.DashboardView} to populate each
     * limit bar island.
     *
     * @param category    the spending category display name (e.g. {@code "Food"})
     * @param limitAmount the user-defined weekly spending cap for this category
     *                    (e.g. {@code 150.00})
     * @param spent       the total dollars spent in this category this week
     *                    (e.g. {@code 87.50})
     * @param fillPercent the bar fill percentage, rounded to 2 decimal places,
     *                    clamped to {@code [0.0, 100.0]} (e.g. {@code 58.33})
     * @param isOver      {@code true} if {@code spent >= limitAmount}, indicating
     *                    the bar should be rendered in red
     */
    public record LimitBarData(
            String category, // category label shown on the bar island
            double limitAmount, // weekly cap set by the user
            double spent, // actual spend this week
            double fillPercent, // visual fill level of the progress bar (0–100)
            boolean isOver // true → render bar in red
    ) {}
}
