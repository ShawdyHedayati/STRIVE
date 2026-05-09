package com.strive.bll;

import com.strive.model.Transaction;
import com.strive.util.DateUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Business logic for the Charts view's two graphs: the Monday–Sunday spending
 * trend line overlay and the all-time average spending bar graph.
 *
 * Like {@link SpendingCalculator} and {@link LimitCalculator}, all methods
 * are stateless — they receive transaction data from the session layer
 * and return display-ready derived values. No state is modified and nothing is
 * persisted here.
 *
 * The two trend lines served by {@link #weekOverlayData} share the same
 * method signature but differ by the {@code currentWeekOnly} flag:
 *
 * {@code true}  → blue "current week" line (only this Mon–Sun's transactions)
 * {@code false} → grey "total average" reference line (all historical transactions)
 *
 * @see SpendingCalculator
 * @see LimitCalculator
 * @see com.strive.ui.ChartsView
 */

public class ChartCalculator {
    /**
     * Produces the average spending per category across <em>all</em> transactions
     * (not just the current week), used to render the average spending bar graph
     * on the Charts view — one bar per category.
     *
     * The average is computed per-transaction (sum of amounts ÷ number of
     * transactions), not per-day. Categories with no transactions are omitted
     * from the result.
     *
     * Insertion order is preserved via {@link LinkedHashMap} so bars render
     * in a consistent sequence in the UI.
     *
     * @param transactions the full in-memory transaction list from the session;
     *                     must not be {@code null}
     * @return a {@link LinkedHashMap} of category display name → average amount
     *         per transaction, rounded to 2 decimal places; empty if the list
     *         is empty
     */
    public Map<String, Double> avgBarGraphData(List<Transaction> transactions) {
        // Group all transactions by category label, regardless of date
        Map<String, List<Transaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::category));

        // For each group, compute the per-transaction average and round it
        Map<String, Double> result = new LinkedHashMap<>(); // preserves category insertion order

        grouped.forEach((cat, txns) -> {
            double avg = txns.stream()
                    .mapToDouble(Transaction::amount)
                    .average()
                    .orElse(0.0); // orElse(0.0) is unreachable here (group is never empty), but safe
            result.put(cat, roundTo2(avg));
        });

        return result;
    }

    /**
     * Produces the daily average spending data for the Mon–Sun trend line chart,
     * returning one averaged value per day of the week.
     *
     * The {@code currentWeekOnly} flag controls which dataset is used:
     *
     *   {@code true}  — restricts to this week's transactions only;
     *       used for the blue "current week" overlay line.
     *   {@code false} — includes all historical transactions;
     *       used for the grey "total average" reference line.
     *
     * All seven days (Mon–Sun) are always present in the returned map, even
     * if a day has no transactions — those days produce {@code 0.0}. This
     * guarantees the chart always has a complete x-axis range with no gaps.
     *
     * @param transactions    the full in-memory transaction list from the session;
     *                        must not be {@code null}
     * @param currentWeekOnly {@code true} to include only the current week's
     *                        transactions; {@code false} to include all transactions
     * @return a {@link LinkedHashMap} of {@link DayOfWeek} → average spending on
     *         that day, rounded to 2 decimal places; always contains all 7 days
     */
    public Map<DayOfWeek, Double> weekOverlayData(List<Transaction> transactions, boolean currentWeekOnly) {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek(); // Monday of the current week

        // Filter to current week if requested; otherwise use the full transaction list
        List<Transaction> filtered = currentWeekOnly
                ? transactions.stream()
                .filter(t -> !t.date().isBefore(startOfWeek))
                .collect(Collectors.toList())
                : transactions;

        // Pre-populate all 7 days so the chart always has a complete Mon–Sun x-axis,
        // even for days with no transactions (they will average to 0.0)
        Map<DayOfWeek, List<Double>> byDay = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) byDay.put(d, new ArrayList<>());

        // Bucket each transaction's amount into the correct day-of-week list
        filtered.forEach(t -> byDay.get(t.date().getDayOfWeek()).add(t.amount()));

        // Average each day's amounts; days with no amounts produce 0.0 via orElse
        Map<DayOfWeek, Double> result = new LinkedHashMap<>();
        byDay.forEach((day, amounts) -> {
            double avg = amounts.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0); // no transactions on this day → 0.0
            result.put(day, roundTo2(avg));
        });

        return result;
    }

    /**
     * Returns all transactions sorted by date descending (most recent first),
     * as displayed in the "All Transactions" island on the Charts view.
     *
     * The input list is not mutated — a new sorted list is returned.
     *
     * @param transactions the full in-memory transaction list from the session;
     *                     must not be {@code null}
     * @return a new {@link List} containing all transactions in descending
     *         date order; empty if the input is empty
     */
    public List<Transaction> allTransactionsSortedByDate(List<Transaction> transactions) {
        return transactions.stream()
                .sorted(Comparator
                        .comparing(Transaction::date)
                        .reversed()) // most recent first
                .collect(Collectors.toList());
    }

    /**
     * Rounds a {@code double} value to 2 decimal places.
     *
     * Used consistently across all chart data methods to match the spec's
     * requirement that hover tooltips display values rounded to 2 decimal places.
     * Uses integer rounding ({@code Math.round}) rather than
     * {@code BigDecimal} for simplicity, which is sufficient for display
     * purposes at this scale.
     *
     * @param value the value to round
     * @return {@code value} rounded to 2 decimal places
     *         (e.g. {@code 3.14159} → {@code 3.14})
     */
    private double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0; // e.g. 3.14159 → 314.159 → 314 → 3.14
    }
}
