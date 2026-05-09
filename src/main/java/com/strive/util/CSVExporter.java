package com.strive.util;

import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Generates a formatted CSV spending report from the current session data.
 *
 * This class is intentionally free of any file I/O. It produces a
 * formatted {@link String} that the caller writes to disk. Keeping file system
 * concerns (file chooser dialogs, {@code FileWriter}, error handling) in the
 * controller layer — where the user interaction lives — and string-building
 * concerns here keeps each layer focused on a single responsibility.
 *
 * The generated report contains three sections in order:
 *
 *   Summary header — generation date and total spending across all transactions
 *   Per-category breakdown — one row per category with total spent, the active
 *       limit (if any), and remaining budget
 *   Full transaction list — every transaction sorted by date descending
 *       (most recent first)
 *
 * @see com.strive.controller.TransactionController
 */

public class CSVExporter {
    /**
     * Generates a complete CSV spending report as a formatted string.
     *
     * Transactions with no matching spending limit in {@code limits} are still
     * included in the category breakdown — the limit column shows {@code "No limit set"}
     * and the remaining column shows {@code "-"} for those rows.
     *
     * The report covers all transactions in the provided list, not
     * just the current week. Callers are responsible for passing the appropriate
     * subset if a narrower date range is desired.
     *
     * @param transactions all transactions to include in the report;
     *                     must not be {@code null}
     * @param limits       all active spending limits used for the category
     *                     breakdown comparison; must not be {@code null}
     * @return the full CSV content as a single string, ready to be written to a
     *         file by the caller; never {@code null}
     */
    public String generate(List<Transaction> transactions, List<SpendingLimit> limits) {
        StringBuilder sb = new StringBuilder();

        // header
        sb.append("STRIVE - Spending Report\n");
        sb.append("Generated:,").append(LocalDate.now()).append("\n\n");

        // Sum all transaction amounts for the top-level total
        double totalSpent = transactions.stream()
                .mapToDouble(Transaction::amount)
                .sum();
        sb.append("Total Spent:,$").append(String.format("%.2f", totalSpent)).append("\n\n");

        // per category breakdown
        sb.append("--- Spending by Category --\n");
        sb.append("Category,Total Spent,Limit,Remaining\n");

        // Group all transactions by category and sum their amounts into one total per category
        Map<String, Double> byCategory = transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::category,
                        Collectors.summingDouble(Transaction::amount)
                ));

        byCategory.forEach((category, spent) -> {
            // Look up the active limit for this category; 0.0 means no limit is set
            double limit = limits.stream()
                    .filter(l -> l.category()
                            .equalsIgnoreCase(category)) // case-insensitive match
                    .mapToDouble(SpendingLimit::amount)
                    .findFirst()
                    .orElse(0.0); // no matching limit found

            // Format the limit and remaining columns:
            // if no limit is set, show "No limit set" and "-" rather than "$0.00"
            String limitStr = limit > 0
                    ? "$" + String.format("%.2f", limit)
                    : "No limit set";
            String remainingStr = limit > 0
                    ? "$" + String.format("%.2f", limit - spent) // may be negative if over limit
                    : "-";

            sb.append(String.format("%s,$%.2f,%s,%s\n",
                    category, spent, limitStr, remainingStr));
        });

        // full transaction list
        sb.append("\n--- All Transactions ---\n");
        sb.append("ID,Date,Category,Amount\n");

        // Sort by date descending so the most recent transactions appear first in the file
        transactions.stream()
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .forEach(t -> sb.append(String.format("%d,%s,%s,$%.2f\n",
                        t.id(), t.date(), t.category(), t.amount())));

        return sb.toString(); // caller is responsible for writing this string to disk
    }
}
