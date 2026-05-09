package com.strive.bll;

import com.strive.util.CategoryRegistry;
import com.strive.model.Transaction;
import com.strive.util.DateUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Business logic for spending aggregation and pie chart data displayed on
 * the dashboard.
 *
 * All methods are stateless — they accept the current transaction
 * list as a parameter, compute derived values, and return results. Nothing
 * is persisted and no session state is modified here. This makes every method
 * independently testable without any setup beyond a plain {@code List<Transaction>}.
 *
 * The "current week" is always defined as Monday through Sunday of the
 * week containing today's date, via {@link DateUtils#startOfCurrentWeek()}.
 *
 * @see LimitCalculator
 * @see ChartCalculator
 * @see com.strive.ui.DashboardView
 */
public class SpendingCalculator {
    /**
     * Groups this week's transactions by category and sums their amounts.
     *
     * Only transactions whose date falls on or after the Monday of the
     * current week are included. Transactions from prior weeks are filtered out.
     *
     * @param transactions the full in-memory transaction list from the session;
     *                     must not be {@code null}
     * @return a map of category display name → total dollars spent in that
     *         category this week; empty if no transactions fall in the current week
     */
    public Map<String, Double> weeklyByCategory(List<Transaction> transactions) {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek(); // Monday of the current week

        return transactions.stream()
                .filter(t -> !t.date().isBefore(startOfWeek)) // keep only this week's transactions
                .collect(Collectors.groupingBy(
                        Transaction::category, // group key: category name
                        Collectors.summingDouble(Transaction:: amount) // aggregate: sum of amounts
                ));
    }

    /**
     * Produces the data needed to render the weekly spending pie chart on the
     * dashboard. Each {@link PieSlice} contains the category name, the total
     * amount spent, and that amount as a percentage of the week's total spending.
     *
     * Slices are sorted by the canonical category order defined in
     * {@link CategoryRegistry} (by enum ordinal), so the chart segments appear
     * in a consistent, predictable sequence regardless of which categories have
     * spending. Categories not found in the registry are sorted to the end.
     *
     * Returns an empty list if there are no transactions this week or if
     * the total spending is zero, preventing division-by-zero in the percentage
     * calculation.
     *
     * @param transactions the full in-memory transaction list from the session;
     *                     must not be {@code null}
     * @return an unmodifiable list of {@link PieSlice}s, one per category that
     *         has spending this week; empty if total spending is zero
     */
    public List<PieSlice> pieChartData(List<Transaction> transactions) {
        Map<String, Double> byCategory = weeklyByCategory(transactions);

        // Sum all category totals to compute each slice's percentage
        double total = byCategory.values().stream().mapToDouble(Double::doubleValue).sum();

        // Guard: return early if there is nothing to chart (avoids division by zero below)
        if (total <= 0) return List.of();

        return byCategory.entrySet().stream()
                .sorted(Comparator.comparingInt(e ->
                        CategoryRegistry.fromDisplayName(e.getKey())
                                .map(Enum::ordinal) // sort by canonical category order
                                .orElse(Integer.MAX_VALUE))) // unknown categories sort last
                .map(e -> new PieSlice(
                        e.getKey(), // category display name
                        e.getValue(), // total dollars spent this week
                        // Round to 2 decimal places: multiply by 10000, round, divide by 100
                        // e.g. 0.33333 → 3333.3 → 3333 → 33.33%
                        // total > 0 is guaranteed by the guard above — no division-by-zero risk
                        Math.round((e.getValue() / total) * 10000.0) / 100.0
                ))
                .toList(); // returns an unmodifiable list
    }

    /**
     * Immutable data carrier representing a single slice of the weekly
     * spending pie chart.
     *
     * Produced exclusively by {@link SpendingCalculator#pieChartData} and
     * consumed by {@link com.strive.ui.DashboardView} to populate the chart.
     *
     * @param category the spending category display name (e.g. {@code "Food"})
     * @param amount   the total dollars spent in this category this week
     *                 (e.g. {@code 142.50})
     * @param percent  this category's share of total weekly spending, rounded
     *                 to two decimal places (e.g. {@code 33.33} represents 33.33%)
     */
    public record PieSlice(String category, double amount, double percent) {}
}
