package com.strive.controller;

import com.strive.bll.CSVExporter;
import com.strive.bll.ChartCalculator;
import com.strive.bll.LimitCalculator;
import com.strive.bll.SpendingCalculator;
import com.strive.model.CategoryRegistry;
import com.strive.model.Transaction;
import com.strive.session.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Handles all user actions related to transactions: add, edit, delete, undo, export, and supply display data to UI
 *
 * On write path, this controller validates input and creates the appropriate
 * {@link Command}, which is applied to the {@link SessionManager}
 * Nothing is written to the db until {@link NavigationController#requestSave()} is called
 *
 * On the read path, this controller calls the bll calculators to
 * transform raw session data into chart ready and display ready formats
 * before returning them to the UI
 */

public class TransactionController {
    private final SessionManager session;
    private final SpendingCalculator spendingCalculator;
    private final LimitCalculator limitCalculator;
    private final ChartCalculator chartCalculator;
    private final CSVExporter csvExporter;

    /**
     * Constructs a TransactionController with its required bll dependencies
     *
     * @param session the session manager shared across all controllers
     */
    public TransactionController(SessionManager session) {
        this.session = session;
        this.spendingCalculator = new SpendingCalculator();
        this.limitCalculator = new LimitCalculator();
        this.chartCalculator = new ChartCalculator();
        this.csvExporter = new CSVExporter();
    }

    /**
     * Validates and adds a new transaction to the session
     * Rejects the action if the cat is not in {@link CategoryRegistry}
     *
     * @param amount the transaction amount in dollars
     * @param category the spending category, must be a valid CategoryRegistry entry
     * @param date the date the transaction occurred
     */
    public void addTransaction(double amount, String category, LocalDate date) {
        if (!CategoryRegistry.isValid(category)) {
            System.err.println("[TransactionController] Invalid category: " + category);
            return;
        }

        Transaction t = new Transaction(session.nextId(), amount, category, date);
        session.apply(new AddCommand<>(t));
        System.out.println("[TransactionController] Added: " + t);
    }

    /**
     * Validates and edits an existing transaction in the session.
     * The previous state is retrieved from the session so it can be
     * stored in the command for undo
     *
     * @param id the ID of the transaction to edit
     * @param amount the new amount
     * @param category the new cat, must be a valid CategoryRegistry entry
     * @param date the new date
     */
    public void editTransaction (int id, double amount, String category, LocalDate date) {
        if (!CategoryRegistry.isValid(category)) {
            System.err.println("[TransactionController] Invalid category: " + category);
            return;
        }

        // retrieve prev state so EditTransactionCommand can restore it on undo
        Transaction previous = session.getTransactions().stream()
                .filter(t -> t.id() == id)
                .findFirst()
                .orElse(null);

        if (previous == null) {
            System.err.println("[TransactionController] Transaction not found: " + id);
            return;
        }

        Transaction updated = new Transaction(id, amount, category, date);
        session.apply(new EditCommand<>(previous, updated));
        System.out.println("[TransactionController] Edit id=" + id);
    }

    /**
     * Deletes a transaction from the session by ID
     * The full transaction is retrieved first so the command can restore
     * it on undo
     *
     * @param id the ID of the transaction to delete
     */
    public void deleteTransaction(int id) {
        // retrieve the full obj so DeleteTransactionCommand can re insert it on undo
        Transaction target = session.getTransactions().stream()
                .filter(t -> t.id() == id)
                .findFirst()
                .orElse(null);

        if (target == null) {
            System.err.println("[TransactionController] Transaction not found: " + id);
            return;
        }

        session.apply(new DeleteCommand<>(target));
        System.out.println("[TransactionController] Deleted id=" + id);
    }

    /**
     * Reverses the most recently applied command via the session manager.
     * Per the spec, only a single step undo is supported
     */
    public void undo() { session.undo(); }

    /**
     * Generates a CSV report and writes it to the given file path.
     * The bll {@link CSVExporter} produces the formatted string;
     * this method handles the file I/O so the bll stays free of sys concerns
     *
     * @param filePath absolute path to write the CSV file
     */
    public void exportCSV(String filePath) {
        // bll generates the content; controller writes it to disk
        String csv = csvExporter.generate(session.getTransactions(), session.getLimits());

        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.print(csv);
            System.out.println("[TransactionController] Exported to: " + filePath);
        } catch (IOException e) {
            System.err.println("[TransactionController] Export failed: " + e.getMessage());
        }
    }

    /**
     * Returns pie chart data for the current week's spending breakdown,
     * formatted for direct use by the dash view
     *
     * @return list of pie slices, on per cat with spending this week
     */
    public List<SpendingCalculator.PieSlice> getPieChartData() {
        return spendingCalculator.pieChartData(session.getTransactions());
    }

    /**
     * Returns limit bar data for all active spending limits,
     * formatting for direct use by the dashboard limit islands
     *
     * @return list of limit bar data records
     */
    public List<LimitCalculator.LimitBarData> getLimitBarData() {
        return limitCalculator.limitBarData(session.getLimits(), session.getTransactions());
    }

    /**
     * Returns all transactions entered today, for display in the Today's
     * Entries island on dahs
     *
     * @return list of today's transaction
     */
    public List<Transaction> getSessionTransactions() {
        return session.getTransactions().stream().toList();
    }

    /**
     * Expose SessionManager so UI controllers can reg themselves as {@link com.strive.session.SessionListener}s
     *
     * @return the shared session manager
     */
    public com.strive.session.SessionManager getSession() { return session; }

    /**
     * Returns the full transaction list from the session, used by the All
     * Transactions island on the charts view
     *
     * @return all transactions in session order
     */
    public List<Transaction> getAllTransactions() {
        return chartCalculator.allTransactionsSortedByDate(session.getTransactions());
    }

    /**
     * Returns daily avg Spedning data for the trend line chart
     * When currentWeekOnly is true, return this week's data
     * When false, return all time avg
     *
     * @param currentWeekOnly true to restrict to current week only
     * @return map of day of week to avg spending, rounded to 2 dec place
     */
    public Map<DayOfWeek, Double> getWeekOverlayData(boolean currentWeekOnly) {
        return chartCalculator.weekOverlayData(session.getTransactions(), currentWeekOnly);
    }

    /**
     * Returns avg spending per cat across all time
     * Used to render avg spending bar graph on chart vie
     *
     * @return map of cat name to avg amnt, rounded 2 dec places
     */
    public Map<String, Double> getAvgBarGraphData() {
        return chartCalculator.avgBarGraphData(session.getTransactions());
    }
}
