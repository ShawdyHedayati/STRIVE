package com.strive.util;

import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a formatted CSV spedning report from the current session data
 *
 * This class is free of any file I/O. It produces a formatted {@link String} that the controller writes to disk
 * Keeping file system concerns in the controller layer where the file dialog lives, not here
 *
 * The report contrains three sections: a summary header, a per-category
 * breakdown with limit comparison, and a full chronological transaction list.
 */

public class CSVExporter {
    /**
     * Generates a complete CSV spending report as a formatted string
     *
     * @param transactions all transactions to include in the report
     * @param limits all active spending limits used for the cat breakdown
     * @return the full CSV content as a string, ready to be written to file
     */
    public String generate(List<Transaction> transactions, List<SpendingLimit> limits) {
        StringBuilder sb = new StringBuilder();

        // header
        sb.append("STRIVE - Spending Report\n");
        sb.append("Generated:,").append(LocalDate.now()).append("\n\n");

        double totalSpent = transactions.stream()
                .mapToDouble(Transaction::amount)
                .sum();
        sb.append("Total Spent:,$").append(String.format("%.2f", totalSpent)).append("\n\n");

        // per category breakdown
        sb.append("--- Spending by Category --\n");
        sb.append("Category,Total Spent,Limit,Remaining\n");

        // group all transactions by category and sum amounts
        Map<String, Double> byCategory = transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::category,
                        Collectors.summingDouble(Transaction::amount)
                ));

        byCategory.forEach((category, spent) -> {
            double limit = limits.stream()
                    .filter(l -> l.category()
                            .equalsIgnoreCase(category))
                    .mapToDouble(SpendingLimit::amount)
                    .findFirst()
                    .orElse(0.0);

            // display "No limit set and "-" when no limit exists for this cat
            String limitStr = limit > 0
                    ? "$" + String.format("%.2f", limit)
                    : "No limit set";
            String remainingStr = limit > 0
                    ? "$" + String.format("%.2f", limit - spent)
                    : "-";

            sb.append(String.format("%s,$%.2f,%s,%s\n",
                    category, spent, limitStr, remainingStr));
        });

        // full transaction list
        sb.append("\n--- All Transactions ---\n");
        sb.append("ID,Date,Category,Amount\n");

        // sort by date descending so the most recent traansactions appear first
        transactions.stream()
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .forEach(t -> sb.append(String.format("%d,%s,%s,$%.2f\n",
                        t.id(), t.date(), t.category(), t.amount())));

        return sb.toString();
    }
}
