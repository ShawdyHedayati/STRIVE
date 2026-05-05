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
 * Central coordinator of the session layer
 * Manages the in memeory state, command log, dirty flag, and db flush lifecycle
 *
 * WRITE PATH
 * When a user takes an action, the controller creates a {@link Command}
 * and calls {@link #apply(Command)}. The command updates the in memory
 * {@link SessionState} immediately and is appended to the command log
 * Nothing is written to the db until {@link #flush()} is explicitly called
 *
 * UNDO
 * Per the spec, undo reverses the single most recent action
 * {@link #undo()} pops the last command from the log and calls its
 * {@link Command#undo(SessionState)} method so no full log replay is needed
 *
 * SAVE / DISCARD
 * {@link #flush()} iterates the full command log and persists each command
 * via the appropriate DAO.
 */

public class SessionManager {
    private final TransactionDAO transactionDAO;
    private final LimitDAO limitDAO;

    // live in memory snapshot; always reflects the current unsaved state
    private final SessionState state;

    // ordered log of all pending commands not yet written to the db
    private final ArrayDeque<Command> commandLog = new ArrayDeque<>();

    private final ArrayDeque<Command> permanentLog = new ArrayDeque<>();

    // true whenever the command log contains commands not yet flushed to db
    private boolean dirty = false;

    // observers notified after every state change
    private final List<SessionListener> listeners = new ArrayList<>();

    /**
     * Constructs a SessionManager and immediately loads the init state
     * from the db via provided DAOs
     *
     * @param transactionDAO DAO for reading and writing transactions
     * @param limitDAO DAO for reading and writing spending limits
     */
    public SessionManager(TransactionDAO transactionDAO, LimitDAO limitDAO) {
        this.transactionDAO = transactionDAO;
        this.limitDAO = limitDAO;
        // load initial state from db so the UI has data on first render
        this.state = new SessionState(
                transactionDAO.fetchAll(),
                limitDAO.fetchAll()
        );

        System.out.println("[SessionManager] Initialized with "
                + state.getTransactions().size() + " transactions, "
                + state.getLimits().size() + " limits.");
    }

    /**
     * Applies a command to the in memory state and appends it to the
     * command log
     * Sets the dirty flag and notifies all registered listeners
     *
     * @param cmd the command to apply
     */
    public void apply (Command cmd) {
        cmd.apply(state);
        commandLog.addLast(cmd);
        dirty = true;
        notifyListeners();
    }

    public void applyPermanent(Command cmd) {
        cmd.apply(state);
        permanentLog.addLast(cmd);
        dirty = true;
        notifyListeners();
    }

    public boolean canUndo() {
        return !commandLog.isEmpty();
    }

    /**
     * Reverses the most recently applied command
     * Per the spec, only a single step undo is supported: the last cmd,
     * is popped and its {@link Command#undo(SessionState)} is called
     * If the log becomes empty after undo, the dirty flag is cleared
     */
    public void undo() {
        if (commandLog.isEmpty()) return;
        Command last = commandLog.pollLast();
        last.undo(state);
        // only clear dirty if log is now empty (no pending changes)
        if (commandLog.isEmpty()) dirty = false;
        notifyListeners();
        System.out.println("[SessionManager] Undo applied.");
    }

    /**
     * Persists all commands in the log to the db via the DAOs,
     * then clears the log and resets the dirty flag
     * This is the only point at which the db is written to
     */
    public void flush() {
        for (Command cmd : permanentLog) {
            persistCommand(cmd);
        }
        permanentLog.clear();
        for (Command cmd : commandLog) {
            persistCommand(cmd);
        }
        commandLog.clear();
        dirty = false;
        System.out.println("[SessionManager] Flushed to database.");
    }

    /**
     * Returns {@code true} if there are unsaved changes in the cmd log
     *
     * @return true if the command log is not empty
     */
    public boolean isDirty() { return dirty; }

    /**
     * Returns the next available ID for a new record, calculated from
     * the current in memory state
     *
     * @return next safe ID
     */
    public int nextId() { return state.nextId(); }

    /**
     * Returns the current in mem list of transaction
     *
     * @return live transaction list
     */
    public ArrayList<Transaction> getTransactions() { return state.getTransactions(); }

    /**
     * Returns the current in memory list of spending limits
     *
     * @return live spending limit list
     */
    public ArrayList<SpendingLimit> getLimits() { return state.getLimits(); }

    /**
     * Registers a listener to be notified on every state change
     *
     * @param listener the listern to add
     */
    public void addListener(SessionListener listener) { listeners.add(listener); }

    // notifies all registered listeners that the session state has changed
    private void notifyListeners() { for (SessionListener l : listeners) l.onSessionUpdated(); }

    private void persistCommand(Command cmd) {
        switch (cmd) {
            case AddCommand<?> c -> persistInsert(c.getRecord());
            case EditCommand<?> c -> persistUpdate(c.getUpdated());  // ← update, not insert
            case DeleteCommand<?> c -> persistDelete(c.getRecord());
            default -> System.err.println("[SessionManager] Unknown command: "
                    + cmd.getClass().getSimpleName());
        }
    }

    private void persistInsert(DBRecord record) {
        if (record instanceof Transaction t)   transactionDAO.insert(t);
        else if (record instanceof SpendingLimit l) limitDAO.insert(l);
    }

    private void persistUpdate(DBRecord record) {
        if (record instanceof Transaction t)   transactionDAO.update(t);
        else if (record instanceof SpendingLimit l) limitDAO.update(l);
    }

    private void persistDelete(DBRecord record) {
        if (record instanceof Transaction t)   transactionDAO.delete(t.id());
        else if (record instanceof SpendingLimit l) limitDAO.delete(l.id());
    }
}
