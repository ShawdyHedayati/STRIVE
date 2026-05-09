package com.strive.session;

import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;
import com.strive.model.DBRecord;
import com.strive.session.command.Command;

import java.util.ArrayList;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Holds the in-memory snapshot of all transactions and spending limits for
 * the current session.
 *
 * On startup, {@link SessionManager} loads both tables from the database
 * into this state. All subsequent reads come from here rather than from the
 * database, keeping the UI fast and the write-path cleanly separated. The
 * database is only written to when {@link SessionManager#flush()} is called.
 *
 * Mutation discipline: The lists inside this class should never be
 * mutated directly by callers. All mutations (add, remove, update) go through
 * the dedicated methods below, which are invoked by {@link Command}
 * implementations. This ensures every change is tracked and reversible via
 * the undo stack.
 *
 * ID space: Transactions and limits share a single ID counter via
 * {@link #nextId()}, preventing collisions across both tables even though they
 * are stored in separate database tables.
 *
 * @see SessionManager
 * @see com.strive.session.command.Command
 */

public class SessionState {
    /** Live list of all transactions for the current session; mutated only through {@link #add}, {@link #remove}, {@link #update}. */
    private final ArrayList<Transaction> transactions;
    /** Live list of all spending limits for the current session; mutated only through {@link #add}, {@link #remove}, {@link #update}. */
    private final ArrayList<SpendingLimit> limits;

    /**
     * Initialises the session state with defensive copies of the given lists.
     *
     * Copying prevents the DAO's original lists from being accidentally
     * mutated through this state object — the DAO retains its own references
     * unaffected.
     *
     * @param transactions initial list of transactions loaded from the database
     * @param limits       initial list of spending limits loaded from the database
     */
    public SessionState(ArrayList<Transaction> transactions, ArrayList<SpendingLimit> limits) {
        this.transactions = new ArrayList<>(transactions); // defensive copy
        this.limits = new ArrayList<>(limits); // defensive copy
    }

    /**
     * Returns the live transaction list.
     *
     * Do not mutate this list directly. Use {@link #add},
     * {@link #remove}, or {@link #update} instead, so that changes are
     * properly tracked by the command layer and remain undoable.
     *
     * @return the mutable list of current transactions; never {@code null}
     */
    public ArrayList<Transaction> getTransactions() { return transactions; }

    /**
     * Returns the live spending limit list.
     *
     * Do not mutate this list directly. Use {@link #add},
     * {@link #remove}, or {@link #update} instead.
     *
     * @return the mutable list of current spending limits; never {@code null}
     */
    public ArrayList<SpendingLimit> getLimits() { return limits; }

    /**
     * Adds a record to the appropriate in-memory list, determined by its
     * runtime type.
     *
     * A {@link Transaction} is appended to {@link #transactions};
     * a {@link SpendingLimit} is appended to {@link #limits}.
     * Any other {@link DBRecord} subtype is silently ignored.
     *
     * @param record the record to add; must not be {@code null}
     */
    public void add(DBRecord record) {
        if (record instanceof Transaction t) transactions.add(t); // route to transaction list
        else if (record instanceof SpendingLimit l) limits.add(l); // route to limit list
    }

    /**
     * Removes the record with the given primary key from whichever list contains it.
     *
     * Both lists are searched — the id namespace is shared across transactions
     * and limits, so a given id will only ever appear in one of them.
     *
     * @param id the primary key of the record to remove
     */
    public void remove(int id) {
        transactions.removeIf(t -> t.id() == id); // no-op if id not in transactions
        limits.removeIf(l -> l.id() == id); // no-op if id not in limits
    }

    /**
     * Replaces the existing record that shares the given record's {@code id}
     * with the new version.
     *
     * Implemented as a remove-then-add, leveraging the fact that both the
     * old and new records share the same {@code id()}. This works cleanly
     * because model records are immutable — there is no in-place field update.
     *
     * @param record the updated record to swap in; must not be {@code null},
     *               and its {@code id()} must match an existing record or the
     *               operation results in a bare add with no prior removal
     */
    public void update(DBRecord record) {
        remove(record.id()); // strip out the old version first
        add(record); // insert the new version in its place
    }

    /**
     * Calculates the next available record ID by finding the current maximum ID
     * across both transactions and limits, then incrementing by one.
     *
     * Transactions and limits share a single ID counter to prevent collisions,
     * even though they live in separate database tables. If both lists are empty,
     * returns {@code 1} as the first valid ID.
     *
     * @return the next safe integer ID to assign to a new {@link DBRecord}
     */
    public int nextId() {
        // Find the highest id currently in use across both lists
        int maxT = transactions.stream().mapToInt(Transaction::id).max().orElse(0);
        int maxL = limits.stream().mapToInt(SpendingLimit::id).max().orElse(0);

        // Return one above the global maximum — safe for both tables
        return Math.max(maxT, maxL) + 1;
    }
}
