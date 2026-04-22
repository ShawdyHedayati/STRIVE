package com.strive.bll;

import com.strive.model.CategoryRegistry;
import com.strive.model.Transaction;
import com.strive.util.DateUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for all spending aggregation and chart data used
 * by the dashboard's pie chart and the charts view's trend line
 *
 * All methods are stateless so they take the current transaction list
 * from the session layer and return derived values.
 * Nothing is persisted here
 */
public class SpendingCalculator {
    /**
     * Groups weekly transaction by category and sums their amounts
     * Only transactions from the current week (Mon-Sun) are included
     *
     * @param transactions the full in memory transaction list
     * @return map of category name to total amount spent this week
     */
    public Map<String, Double> weeklyByCategory(List<Transaction> transactions) {
        LocalDate startOfWeek = DateUtils.startOfCurrentWeek();

        return transactions.stream()
                .filter(t -> !t.date().isBefore(startOfWeek))
                .collect(Collectors.groupingBy(
                        Transaction::category,
                        Collectors.summingDouble(Transaction:: amount)
                ));
    }

    /**
     * Produces the data needed to render the weekly pie chart on the dash
     * Each slice contain the category name, amount, percent of total, and color
     * Returns an empty list if there are no transactions this week
     *
     * @param transactions the full in memory transaction list
     * @return list of pie slices, one per category with spending this week
     */
    public List<PieSlice> pieChartData(List<Transaction> transactions) {
        Map<String, Double> byCategory = weeklyByCategory(transactions);
        double total = byCategory.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0) return List.of();

        return byCategory.entrySet().stream()
                .map(e -> new PieSlice(
                        e.getKey(),
                        e.getValue(),
                        // round 2 dec places for display
                        total == 0 ? 0 : Math.round((e.getValue() / total) * 10000.0) / 100.0,
                        CategoryRegistry.colorFor(e.getKey())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Immutable data carrier for a single slice of the weekly pie chart
     *
     * @param category the spending category
     * @param amount total amount spent in this category this week
     * @param percent percentage of total weekly spending (0-100, 2 dec places)
     * @param color hex color from {@link CategoryRegistry}
     */
    public record PieSlice(String category, double amount, double percent, String color) {}
}
