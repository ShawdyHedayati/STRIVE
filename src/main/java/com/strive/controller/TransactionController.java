package com.strive.controller;

import com.strive.util.CSVExporter;
import com.strive.bll.ChartCalculator;
import com.strive.bll.LimitCalculator;
import com.strive.bll.SpendingCalculator;
import com.strive.util.CategoryRegistry;
import com.strive.model.Transaction;
import com.strive.session.*;
import com.strive.session.command.AddCommand;
import com.strive.session.command.Command;
import com.strive.session.command.DeleteCommand;
import com.strive.session.command.EditCommand;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Handles all user actions related to transactions: adding, editing, deleting,
 * undoing, exporting to CSV, and supplying display-ready data to the UI.
 *
 * Write path: Mutation methods ({@link #addTransaction},
 * {@link #editTransaction}, {@link #deleteTransaction}) validate input, create
 * the appropriate {@link Command}, and route it through
 * {@link SessionManager#apply(Command)} — placing it on the undoable command
 * log. Nothing is written to the database until
 * {@link NavigationController#requestSave()} is called explicitly.
 *
 * Read path: Query methods delegate to the BLL calculators
 * ({@link SpendingCalculator}, {@link LimitCalculator}, {@link ChartCalculator})
 * to transform raw session data into chart-ready and display-ready formats
 * before returning them to the UI. No BLL logic lives in this class.
 *
 * Undo: Transaction mutations are undoable via {@link #undo()}.
 * Limit mutations (handled by {@link LimitController}) are not.
 *
 * Preloaded IDs: {@link #preloadedTransactionIds} captures the set
 * of transaction IDs that existed at startup. {@link #getSessionTransactions()}
 * uses this to return only transactions added during the current session —
 * useful for highlighting new entries in the UI.
 *
 * @see LimitController
 * @see NavigationController
 * @see com.strive.config.AppContext
 */

public class TransactionController {
    /** The shared session manager; all transaction mutations route through this. */
    private final SessionManager session;
    /** Computes weekly spending totals and pie chart slice data. */
    private final SpendingCalculator spendingCalculator;
    /** Computes limit bar fill percentages and over-limit flags. */
    private final LimitCalculator limitCalculator;
    /** Computes trend line overlay data and all-time average bar graph data. */
    private final ChartCalculator chartCalculator;
    /** Generates the formatted CSV spending report string. */
    private final CSVExporter csvExporter;

    /**
     * Snapshot of transaction IDs that existed in the session at construction
     * time (i.e. loaded from the database on startup). Used by
     * {@link #getSessionTransactions()} to distinguish pre-existing records
     * from newly added ones.
     */
    private final Set<Integer> preloadedTransactionIds;

    /**
     * Constructs a {@code TransactionController} with all required BLL dependencies.
     *
     * Captures the current set of transaction IDs as {@link #preloadedTransactionIds}
     * immediately at construction, before any user-initiated adds can occur.
     *
     * @param session            the session manager shared across all controllers;
     *                           must not be {@code null}
     * @param spendingCalculator BLL calculator for spending aggregation; must not be {@code null}
     * @param limitCalculator    BLL calculator for limit bar data; must not be {@code null}
     * @param chartCalculator    BLL calculator for chart overlay and bar graph data; must not be {@code null}
     * @param csvExporter        utility for generating the CSV report string; must not be {@code null}
     */
    public TransactionController(SessionManager session,
                                 SpendingCalculator spendingCalculator,
                                 LimitCalculator limitCalculator,
                                 ChartCalculator chartCalculator,
                                 CSVExporter csvExporter) {
        this.session = session;
        this.spendingCalculator = spendingCalculator;
        this.limitCalculator = limitCalculator;
        this.chartCalculator = chartCalculator;
        this.csvExporter = csvExporter;

        // Snapshot the IDs of all transactions already in the session at startup,
        // so getSessionTransactions() can later filter to only new additions
        this.preloadedTransactionIds = session.getTransactions().stream()
                .map(Transaction::id)
                .collect(Collectors.toSet());
    }

    /**
     * Validates and adds a new transaction to the session.
     *
     * The operation is rejected (logged to stderr, no state change) if the
     * category is not a valid {@link CategoryRegistry} entry.
     *
     * Routes through {@link SessionManager#apply} — this change is
     * undoable via {@link #undo()}.
     *
     * @param amount   the transaction amount in dollars; should be &gt; 0
     * @param category the spending category; must be a valid {@link CategoryRegistry}
     *                 display name (case-insensitive)
     * @param date     the date the transaction occurred; must not be {@code null}
     */
    public void addTransaction(double amount, String category, LocalDate date) {
        if (!CategoryRegistry.isValid(category)) {
            System.err.println("[TransactionController] Invalid category: " + category);
            return;
        }

        // Assign the next globally unique ID and build the immutable record
        Transaction t = new Transaction(session.nextId(), amount, category, date);
        session.apply(new AddCommand<>(t)); // undoable — pushed onto the command log
        System.out.println("[TransactionController] Added: " + t);
    }

    /**
     * Validates and replaces an existing transaction with updated values.
     *
     * The previous state is retrieved from the session and stored in the
     * {@link EditCommand} so it can be restored exactly on undo.
     *
     * The operation is rejected (logged to stderr, no state change) if:
     *   The new category is not a valid {@link CategoryRegistry} entry, or
     *   No transaction with the given {@code id} exists in the session.
     *
     * Routes through {@link SessionManager#apply} — this change is
     * undoable via {@link #undo()}.
     *
     * @param id       the primary key of the transaction to edit
     * @param amount   the replacement amount in dollars
     * @param category the replacement category; must be a valid {@link CategoryRegistry} entry
     * @param date     the replacement date; must not be {@code null}
     */
    public void editTransaction (int id, double amount, String category, LocalDate date) {
        if (!CategoryRegistry.isValid(category)) {
            System.err.println("[TransactionController] Invalid category: " + category);
            return;
        }

        // Retrieve the existing record — needed as EditCommand's "previous" snapshot for undo
        Transaction previous = session.getTransactions().stream()
                .filter(t -> t.id() == id)
                .findFirst()
                .orElse(null);

        if (previous == null) {
            System.err.println("[TransactionController] Transaction not found: " + id);
            return;
        }

        Transaction updated = new Transaction(id, amount, category, date);
        session.apply(new EditCommand<>(previous, updated)); // undoable — pushed onto the command log
        System.out.println("[TransactionController] Edit id=" + id);
    }

    /**
     * Removes a transaction from the session by its primary key.
     *
     * The full transaction record is retrieved before deletion so the
     * {@link DeleteCommand} can restore it completely on undo.
     *
     * If no transaction with the given {@code id} exists, the operation is
     * a no-op (logged to stderr, no state change).
     *
     * Routes through {@link SessionManager#apply} — this change is
     * undoable via {@link #undo()}.
     *
     * @param id the primary key of the transaction to delete
     */
    public void deleteTransaction(int id) {
        // Retrieve the full record — DeleteCommand needs it to re-insert on undo
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
     * Reverses the most recently applied transaction command.
     *
     * Delegates directly to {@link SessionManager#undo()}. Per the spec,
     * only a single-step undo is supported — but this method can be called
     * repeatedly to unwind multiple commands if they remain on the log.
     * Limit mutations are never undone by this method.
     */
    public void undo() { session.undo(); }

    /**
     * Returns {@code true} if there is at least one undoable transaction
     * command on the log.
     *
     * @return {@code true} if {@link #undo()} would have an effect;
     *         delegates to {@link SessionManager#canUndo()}
     */
    public boolean canUndo() { return session.canUndo(); }

    /**
     * Generates a CSV spending report and writes it to the given file path.
     *
     * Follows the separation established in {@link CSVExporter}: the BLL
     * generates the formatted string; this method handles the file I/O, keeping
     * system concerns out of the BLL layer. Any {@link IOException} is caught
     * and logged to stderr rather than re-thrown.
     *
     * @param filePath the absolute path of the file to write;
     *                 the file is created or overwritten if it already exists
     */
    public void exportCSV(String filePath) {
        // BLL produces the formatted CSV string; controller writes it to disk
        String csv = csvExporter.generate(session.getTransactions(), session.getLimits());

        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.print(csv);
            System.out.println("[TransactionController] Exported to: " + filePath);
        } catch (IOException e) {
            System.err.println("[TransactionController] Export failed: " + e.getMessage());
        }
    }

    /**
     * Returns pie chart slice data for the current week's spending breakdown,
     * formatted for direct use by the Dashboard view.
     *
     * @return list of {@link SpendingCalculator.PieSlice}s, one per category
     *         with spending this week; empty if no transactions this week
     */
    public List<SpendingCalculator.PieSlice> getPieChartData() {
        return spendingCalculator.pieChartData(session.getTransactions());
    }

    /**
     * Returns limit bar data for all active spending limits, formatted for
     * direct use by the Dashboard limit island widgets.
     *
     * @return list of {@link LimitCalculator.LimitBarData} records, one per
     *         active limit; empty if no limits are set
     */
    public List<LimitCalculator.LimitBarData> getLimitBarData() {
        return limitCalculator.limitBarData(session.getLimits(), session.getTransactions());
    }

    /**
     * Returns only the transactions that were added during the current session
     * (i.e. after startup), by filtering out IDs captured in
     * {@link #preloadedTransactionIds}.
     *
     * Used by the UI to highlight or scope newly entered transactions
     * separately from pre-existing ones.
     *
     * @return an unmodifiable list of transactions added since startup;
     *         empty if no new transactions have been added
     */
    public List<Transaction> getSessionTransactions() {
        return session.getTransactions().stream()
                .filter(t -> !preloadedTransactionIds.contains(t.id())) // exclude pre-existing IDs
                .toList();
    }

    /**
     * Registers a {@link SessionListener} to be notified after every
     * state-changing operation.
     *
     * Delegates to {@link SessionManager#addListener(SessionListener)}.
     * Views call this during their {@code initialize()} to wire themselves
     * into the observer chain.
     *
     * @param listener the listener to register; must not be {@code null}
     */
    public void addListener(SessionListener listener) {
        session.addListener(listener);
    }

    /**
     * Returns all transactions sorted by date descending (most recent first),
     * for use in the "All Transactions" island on the Charts view.
     *
     * @return a sorted list of all transactions; empty if the session has none
     */
    public List<Transaction> getAllTransactions() {
        return chartCalculator.allTransactionsSortedByDate(session.getTransactions());
    }

    /**
     * Returns daily average spending data for the Mon–Sun trend line chart.
     *
     * @param currentWeekOnly {@code true} to return only the current week's
     *                        data (blue overlay line); {@code false} to return
     *                        all-time averages (grey reference line)
     * @return a map of {@link DayOfWeek} → average spending, rounded to 2
     *         decimal places; always contains all 7 days
     */
    public Map<DayOfWeek, Double> getWeekOverlayData(boolean currentWeekOnly) {
        return chartCalculator.weekOverlayData(session.getTransactions(), currentWeekOnly);
    }

    /**
     * Returns the average spending per category across all time, used to
     * render the average spending bar graph on the Charts view.
     *
     * @return a map of category display name → average amount per transaction,
     *         rounded to 2 decimal places; empty if the session has no transactions
     */
    public Map<String, Double> getAvgBarGraphData() {
        return chartCalculator.avgBarGraphData(session.getTransactions());
    }
}
