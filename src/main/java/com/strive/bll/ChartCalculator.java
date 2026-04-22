package com.strive.bll;

import com.strive.model.Transaction;
import com.strive.util.DateUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for the charts view's two graphs:
 * the Monday-Sunday trend line overlay and the average spending bar graph.
 *
 * All  methods are stateless, they reveive transaction data from the session layer and return derived display-ready values.
 * No persistence occurs here
 */

public class ChartCalculator {
    /**
     * Produces average spending per category across all transactions
     * (not just current week)
     * Used to render the average spending bar graph on the charts view
     * One bar per category
     *
     * @param transactions the full in-memory transaction list
     * @return map of category name to average amount per transaction, rounded to 2 decimal places
     */
    public Map<String, Double> avgBarGraphData(List<Transaction> transactions) {
        // group all transactions by category then average each group
        Map<String, List<Transaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::category));

        Map<String, Double> result = new LinkedHashMap<>();
        grouped.forEach((cat, txns) -> {
            double avg = txns.stream().mapToDouble(Transaction::amount)
                    .average().orElse(0.0);
            result.put(cat, roundTo2(avg));
        });
        return result;
    }

    /**
     * Produces the daily average spending data for the trend line chart.
     * Returns on value per day of the week (Mon-Sun)
     *
     * When {@code currentWeekOnly} is {@code true}, only this week's transactions are included
     * Used for the blue "current overlay line.
     * When {@code false}, all transactions are included
     * Used for grey "total average" reference line
     *
     * @param transactions the full in-memory transaction list
     * @param currentWeekOnly true to restrict to the current week only
     * @return may of day of week to average spending on that day, rounded to 2 dec places
     */
    public Map<DayOfWeek, Double> weekOverlayData(List<Transaction> transactions, boolean currentWeekOnly) {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek();

        // filter to current week if requested, otherwise use all transactions
        List<Transaction> filtered = currentWeekOnly
                ? transactions.stream()
                .filter(t -> !t.date().isBefore(startOfWeek))
                .collect(Collectors.toList())
                :transactions;

        // pre-populate all 7 days so the chart always has a full mon-sun range
        // even if there are no transactions on some days
        Map<DayOfWeek, List<Double>> byDay = new LinkedHashMap<>();

        for (DayOfWeek d : DayOfWeek.values()) byDay.put(d, new ArrayList<>());

        filtered.forEach(t -> byDay.get(t.date().getDayOfWeek()).add(t.amount()));

        // acg each day's amounts; days with no transactions produce 0.0
        Map<DayOfWeek, Double> result = new LinkedHashMap<>();
        byDay.forEach((day, amounts) -> {
            double avg = amounts.stream().mapToDouble(
                    Double::doubleValue).average().orElse(0.0);
            result.put(day, roundTo2(avg));
        });
        return result;
    }

    /**
     * Returns all transactions sorted by date desc (most recent to first)
     * as displayed in the All Transactions island on the charts view
     *
     * @param transactions the full in-memory transaction list
     * @return new sorted list so does not mutate the input
     */
    public List<Transaction> allTransactionsSortedByDate(List<Transaction> transactions) {
        return transactions.stream()
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Rounds a value to 2 decimal places
     * Used consistently across chart data to match the spec's req
     * for hover tooltips rounded to 2 decimal points
     *
     * @param value the value to round
     * @return value rounded to 2 dec placed
     */
    public double roundTo2(double value) { return Math.round(value * 100.0) / 100.0; }
}
