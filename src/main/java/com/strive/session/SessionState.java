package com.strive.session;

import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;
import com.strive.model.DBRecord;

import java.util.ArrayList;

/**
 * Holds in memory snapshot of all transaction and spending limits for the current session
 *
 * On startup, {@link SessionManager} loads the database into this state
 * All subsequent reads come from here, not the db, keeping the UI fast and
 * the db write-path separate. Mutations are applied by {@link Command}
 * implementations rather than called directly
 */

public class SessionState {
    private final ArrayList<Transaction> transactions;
    private final ArrayList<SpendingLimit> limits;

    /**
     * Init the session state with a defensive copy of the given lists
     * Copying prevents the DAOs' original lists from being mutated via this state.
     *
     * @param transactions initial list of transactions loaded from the db
     * @param limits initial list of spending limits loaded from the db
     */
    public SessionState(ArrayList<Transaction> transactions, ArrayList<SpendingLimit> limits) {
        this.transactions = new ArrayList<>(transactions);
        this.limits = new ArrayList<>(limits);
    }

    /**
     * Returns the live transaction list. Callers should not mutattes this list directly
     * so use the mutation methods below, which are called by {@link Command} implementations
     *
     * @return mutable list of current transactions
     */
    public ArrayList<Transaction> getTransactions() { return transactions; }

    /**
     * Returns the live spending limit list
     *
     * @return mutable list of current spending limits
     */
    public ArrayList<SpendingLimit> getLimits() { return limits; }

    public void add(DBRecord record) {
        if (record instanceof Transaction t) transactions.add(t);
        else if (record instanceof SpendingLimit l) limits.add(l);
    }

    public void remove(int id) {
        transactions.removeIf(t -> t.getID() == id);
        limits.removeIf(l -> l.getID() == id);
    }

    public void update(DBRecord record) {
        remove(record.getID());
        add(record);
    }

    /**
     * Calculates the next avail ID by finding the current max ID
     * both transactions and limits, then incrementing by one.
     * Ensures no ID collisions between the two tables
     *
     * @return next safe ID to use for a new record
     */
    public int nextId() {
        int maxT = transactions.stream().mapToInt(Transaction::id).max().orElse(0);
        int maxL = limits.stream().mapToInt(SpendingLimit::id).max().orElse(0);
        return Math.max(maxT, maxL) + 1;
    }
}
