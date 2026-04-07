package com.cobra;

import com.cobra.types.Transaction;
import com.cobra.types.Limit;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public class CSVExporter {
    public static void export(String path, ArrayList<Transaction> transactions, ArrayList<Limit> limits) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("STRIVE- Spending Report");
            pw.println("Generated:," + java.time.LocalDate.now());
            pw.println();

            double totalSpent = transactions.stream()
                    .mapToDouble(Transaction::amount).sum();
            pw.println("Total Spent:,$" + String.format("%.2f", totalSpent));
            pw.println();

            pw.println("--- Spending by Category ---");
            pw.println("Category,Total Spent,Limit,Remaining");

            Map<String, Double> byCategory = transactions.stream()
                    .collect(Collectors.groupingBy(
                            Transaction::category,
                            Collectors.summingDouble(Transaction::amount)));

            byCategory.forEach((category, spent) -> {
                double limit = limits.stream()
                        .filter(l -> l.category().equalsIgnoreCase(category))
                        .mapToDouble(Limit::amount)
                        .findFirst().orElse(0.0);

                double remaining = limit - spent;
                String limitStr = limit > 0 ? "$" + String.format("%.2f", limit) : "No limit set";
                String remStr = limit > 0 ? "$" + String.format("%.2f", remaining) : "-";

                pw.printf("%s,s%.2f,%s,%s%n", category, spent, limitStr, remStr);
            });

            pw.println();

            pw.println("--- All Transactions ---");
            pw.println("ID,Date,Category,Amount");

            transactions.stream()
                    .sorted(Comparator.comparing(Transaction::date).reversed())
                    .forEach(t -> pw.printf("%d,%s,%s,$%.2f%n", t.id(), t.date(), t.category(), t.amount()));
        }
    }
}
