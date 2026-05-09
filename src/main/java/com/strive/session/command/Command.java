package com.strive.session.command;

import com.strive.session.SessionManager;
import com.strive.session.SessionState;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Represents a single, reversible user action in the session's command log.
 *
 * CoBRA uses the Command pattern to track every mutation a user makes
 * (add, edit, delete a transaction). Each action is wrapped in a {@code Command}
 * implementation and passed to {@link SessionManager#apply(Command)}, which
 * executes it against the in-memory {@link SessionState} and pushes it onto the
 * undo stack.
 *
 * Persistence: Commands only touch in-memory state. Nothing is written
 * to the database until {@link SessionManager#flush()} is explicitly called,
 * which drains the log and issues the corresponding SQL via the DAO layer.
 *
 * Undo: Each command knows how to reverse its own effect via
 * {@link #undo(SessionState)}, making single-step undo straightforward without
 * replaying the entire log from the beginning. When {@link SessionManager#undo()}
 * is called, it pops the most recent command off the stack and invokes
 * {@code undo()} on it.
 *
 * Permanent operations: Not all mutations go through this interface.
 * Limit operations (add, edit, delete, weekly reset) bypass the undo stack by
 * going through {@link SessionManager#applyPermanent(Command)} instead, so they
 * are never accidentally reversed by an undo.
 *
 * @see AddCommand
 * @see EditCommand
 * @see DeleteCommand
 * @see SessionManager#apply(Command)
 * @see SessionManager#undo()
 */

public interface Command {
    /**
     * Applies this command's effect to the given in-memory session state.
     *
     * Implementations should mutate {@code state} to reflect the user's
     * action — for example, appending a new {@link com.strive.model.Transaction}
     * to the transaction list. This method is called by
     * {@link SessionManager#apply(Command)} immediately after the command is
     * created, before any database write occurs.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    void apply(SessionState state);

    /**
     * Reverses this command's effect on the given in-memory session state,
     * restoring it to how it looked before {@link #apply(SessionState)} was called.
     *
     * Called by {@link SessionManager#undo()} on the most recently applied
     * command in the log. Implementations should mirror the logic in
     * {@link #apply} — e.g. if {@code apply} added a record, {@code undo}
     * should remove it, and vice versa.
     *
     * Note: {@code undo()} only reverts in-memory state. If the session has
     * already been flushed to the database, this method alone will not roll back
     * the persisted data.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    void undo(SessionState state);
}
