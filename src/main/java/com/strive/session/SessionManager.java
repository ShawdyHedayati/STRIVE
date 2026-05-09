package com.strive.session;

import com.strive.model.*;
import com.strive.model.dao.LimitDAO;
import com.strive.model.dao.TransactionDAO;
import com.strive.session.command.AddCommand;
import com.strive.session.command.Command;
import com.strive.session.command.DeleteCommand;
import com.strive.session.command.EditCommand;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Central coordinator of CoBRA's session layer.
 *
 * Manages the in-memory {@link SessionState}, the command logs, the dirty
 * flag, and the database flush lifecycle. All other layers (controllers, views)
 * interact with session data exclusively through this class.
 *
 * Write path (undoable transactions):
 * A controller creates a {@link Command} and calls {@link #apply(Command)}.
 * {@code apply} executes the command against {@link SessionState} immediately
 *       and appends it to {@link #commandLog}.
 * Nothing is written to the database until {@link #flush()} is explicitly called.
 *
 * Write path (permanent limit operations):
 * A controller calls {@link #applyPermanent(Command)} instead of {@link #apply}.
 * The command is routed to {@link #permanentLog}, bypassing the undo stack entirely.
 * Limit mutations are never reversible by {@link #undo()} — this is intentional.
 *
 * Undo:
 * {@link #undo()} pops the last command from {@link #commandLog} and calls
 * {@link Command#undo(SessionState)} on it. Only a single-step undo is supported
 * per the spec. Commands in {@link #permanentLog} are never undone.
 *
 * Flush / Save:
 * {@link #flush()} drains both logs in order — permanent first, then undoable —
 * issuing the appropriate DAO call for each command. After flushing, both logs are
 * cleared and the dirty flag is reset.
 *
 * @see SessionState
 * @see com.strive.session.command.Command
 * @see SessionListener
 */

public class SessionManager {
    /** DAO for reading and writing {@link Transaction} records to the database. */
    private final TransactionDAO transactionDAO;
    /** DAO for reading and writing {@link SpendingLimit} records to the database. */
    private final LimitDAO limitDAO;

    /** Live in-memory snapshot; always reflects the current unsaved state after commands are applied. */
    private final SessionState state;

    /**
     * Ordered log of undoable commands (transaction add/edit/delete) not yet
     * written to the database. Supports single-step undo via {@link #undo()}.
     */
    private final ArrayDeque<Command> commandLog = new ArrayDeque<>();

    /**
     * Ordered log of permanent commands (limit add/edit/delete/reset) not yet
     * written to the database. These commands bypass the undo stack and are
     * flushed before {@link #commandLog} entries.
     */
    private final ArrayDeque<Command> permanentLog = new ArrayDeque<>();

    /**
     * {@code true} whenever either log contains commands not yet flushed to the
     * database; used to prompt the user to save before closing.
     */
    private boolean dirty = false;

    /** Observers notified after every state-changing operation. */
    private final List<SessionListener> listeners = new ArrayList<>();

    /**
     * Constructs a {@code SessionManager} and immediately hydrates the in-memory
     * state from the database via the provided DAOs.
     *
     * After construction, {@link #getTransactions()} and {@link #getLimits()}
     * are ready to serve data to the UI without any additional database calls.
     *
     * @param transactionDAO DAO for reading and writing transactions; must not be {@code null}
     * @param limitDAO       DAO for reading and writing spending limits; must not be {@code null}
     */
    public SessionManager(TransactionDAO transactionDAO, LimitDAO limitDAO) {
        this.transactionDAO = transactionDAO;
        this.limitDAO = limitDAO;

        // Load initial state from the database so the UI has data on first render
        this.state = new SessionState(
                transactionDAO.fetchAll(),
                limitDAO.fetchAll()
        );

        System.out.println("[SessionManager] Initialized with "
                + state.getTransactions().size() + " transactions, "
                + state.getLimits().size() + " limits.");
    }

    /**
     * Applies a command to the in-memory state and appends it to the undoable
     * command log.
     *
     * Use this for all transaction mutations (add, edit, delete) that should
     * be reversible via {@link #undo()}. Sets the dirty flag and notifies all
     * registered {@link SessionListener}s.
     *
     * @param cmd the command to apply; must not be {@code null}
     */
    public void apply (Command cmd) {
        cmd.apply(state); // mutate in-memory state immediately
        commandLog.addLast(cmd);// push onto the undo stack
        dirty = true;
        notifyListeners();
    }

    /**
     * Applies a command to the in-memory state and appends it to the permanent
     * log, bypassing the undo stack.
     *
     * Use this for all limit mutations (add, edit, delete, weekly reset)
     * that must not be reversible by {@link #undo()}. Sets the dirty
     * flag and notifies all registered {@link SessionListener}s.
     *
     * @param cmd the command to apply permanently; must not be {@code null}
     */
    public void applyPermanent(Command cmd) {
        cmd.apply(state); // mutate in-memory state immediately
        permanentLog.addLast(cmd); // push onto the permanent log — NOT the undo stack
        dirty = true;
        notifyListeners();
    }

    /**
     * Returns {@code true} if there is at least one undoable command in the log.
     *
     * Note: permanent commands in {@link #permanentLog} are never undoable,
     * so this only reflects the state of {@link #commandLog}.
     *
     * @return {@code true} if {@link #undo()} would have an effect
     */
    public boolean canUndo() {
        return !commandLog.isEmpty();
    }

    /**
     * Reverses the most recently applied undoable command.
     *
     * Pops the last entry from {@link #commandLog} and calls
     * {@link Command#undo(SessionState)} on it. If the log is empty after the
     * undo, the dirty flag is cleared (no more pending changes). If the log still
     * has entries, the session remains dirty.
     *
     * Per the spec, only a single-step undo is meaningful — but this method
     * can be called repeatedly to unwind multiple commands if they are on the log.
     * Commands in {@link #permanentLog} are never affected.
     */
    public void undo() {
        if (commandLog.isEmpty()) return; // nothing to undo — guard against spurious calls

        Command last = commandLog.pollLast(); // pop the most recent undoable command
        last.undo(state); // reverse its effect on in-memory state

        // Only clear dirty if the undo stack is now fully empty
        if (commandLog.isEmpty()) dirty = false;
        notifyListeners();
        System.out.println("[SessionManager] Undo applied.");
    }

    /**
     * Persists all pending commands to the database via the DAOs, then clears
     * both logs and resets the dirty flag.
     *
     * This is the only point at which the database is written to.
     * Permanent commands are flushed first (limit mutations), followed by
     * undoable commands (transaction mutations), preserving correct ordering
     * in case of any cross-table dependencies.
     *
     * After this method returns, both {@link #commandLog} and
     * {@link #permanentLog} are empty and {@link #isDirty()} returns
     * {@code false}.
     */
    public void flush() {
        // Flush permanent (limit) commands first — they cannot be undone
        for (Command cmd : permanentLog) {
            persistCommand(cmd);
        }
        permanentLog.clear();

        // Flush undoable (transaction) commands second
        for (Command cmd : commandLog) {
            persistCommand(cmd);
        }
        commandLog.clear();

        dirty = false;
        System.out.println("[SessionManager] Flushed to database.");
    }

    /**
     * Returns {@code true} if either command log contains commands not yet
     * flushed to the database.
     *
     * @return {@code true} if there are unsaved changes
     */
    public boolean isDirty() { return dirty; }

    /**
     * Returns the next available record ID, calculated from the current
     * in-memory state across both transactions and limits.
     *
     * @return the next safe integer ID to assign to a new {@link DBRecord}
     * @see SessionState#nextId()
     */
    public int nextId() { return state.nextId(); }

    /**
     * Returns the live in-memory list of transactions.
     *
     * @return current transaction list; never {@code null}
     * @see SessionState#getTransactions()
     */
    public ArrayList<Transaction> getTransactions() { return state.getTransactions(); }

    /**
     * Returns the live in-memory list of spending limits.
     *
     * @return current spending limit list; never {@code null}
     * @see SessionState#getLimits()
     */
    public ArrayList<SpendingLimit> getLimits() { return state.getLimits(); }

    /**
     * Registers a {@link SessionListener} to be notified after every
     * state-changing operation.
     *
     * @param listener the listener to add; must not be {@code null}
     */
    public void addListener(SessionListener listener) { listeners.add(listener); }

    /**
     * Notifies all registered listeners that the session state has changed.
     * Called internally after every apply, undo, and flush.
     */
    private void notifyListeners() { for (SessionListener l : listeners) l.onSessionUpdated(); }

    /**
     * Dispatches a single command to the correct DAO write method based on
     * the command's runtime type.
     *
     * Uses Java 21 pattern-matching switch to cleanly handle the three
     * command types. An unknown command type logs a warning to stderr rather
     * than throwing, to avoid disrupting the flush of other commands.
     *
     * @param cmd the command to persist; must not be {@code null}
     */
    private void persistCommand(Command cmd) {
        switch (cmd) {
            case AddCommand<?> c -> persistInsert(c.getRecord());
            case EditCommand<?> c -> persistUpdate(c.getUpdated());  // update, not re-insert
            case DeleteCommand<?> c -> persistDelete(c.getRecord());
            default -> System.err.println("[SessionManager] Unknown command: "
                    + cmd.getClass().getSimpleName());
        }
    }

    /**
     * Issues an INSERT via the appropriate DAO based on the record's runtime type.
     *
     * @param record the record to insert; must be a {@link Transaction} or {@link SpendingLimit}
     */
    private void persistInsert(DBRecord record) {
        if (record instanceof Transaction t)   transactionDAO.insert(t);
        else if (record instanceof SpendingLimit l) limitDAO.insert(l);
    }

    /**
     * Issues an UPDATE via the appropriate DAO based on the record's runtime type.
     *
     * @param record the record to update; must be a {@link Transaction} or {@link SpendingLimit}
     */
    private void persistUpdate(DBRecord record) {
        if (record instanceof Transaction t)   transactionDAO.update(t);
        else if (record instanceof SpendingLimit l) limitDAO.update(l);
    }

    /**
     * Issues a DELETE via the appropriate DAO based on the record's runtime type.
     *
     * @param record the record to delete; only its {@code id()} is used
     */
    private void persistDelete(DBRecord record) {
        if (record instanceof Transaction t)   transactionDAO.delete(t.id());
        else if (record instanceof SpendingLimit l) limitDAO.delete(l.id());
    }
}
