package com.strive.session.command;

import com.strive.model.DBRecord;
import com.strive.session.SessionState;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * A {@link Command} that removes an existing {@link DBRecord} from the
 * in-memory session state.
 *
 * The full record is retained in this command even after deletion so that
 * {@link #undo} can restore it exactly — including its original {@code id},
 * {@code amount}, {@code category}, and {@code date} — without any trip back
 * to the database.
 *
 * <p>The apply/undo symmetry is the exact inverse of {@link AddCommand}:
 * where {@code AddCommand.apply} calls {@code state.add} and
 * {@code AddCommand.undo} calls {@code state.remove}, this command does the
 * opposite in each method.
 *
 * Like {@link AddCommand}, this command is used exclusively for
 * {@link com.strive.model.Transaction} deletions. Limit deletions go through
 * {@link com.strive.session.SessionManager#applyPermanent} and never reach
 * the undo stack.
 *
 * @param <T> the type of record being deleted; must implement {@link DBRecord}
 *            so the apply step can look it up by {@code id}
 *
 * @see AddCommand
 * @see EditCommand
 * @see com.strive.session.SessionManager#apply(Command)
 */

public class DeleteCommand<T extends DBRecord> implements Command {
    /**
     * The full record being deleted, held in memory so {@link #undo} can
     * re-insert it without a database read.
     */
    private final T record;

    /**
     * Constructs a {@code DeleteCommand} for the given record.
     *
     * The entire record — not just its {@code id} — must be passed here
     * so that {@link #undo} has everything it needs to restore the record
     * to session state.
     *
     * @param record the record to delete; must not be {@code null}
     */
    public DeleteCommand(T record) { this.record = record; }

    /**
     * Removes the record from session state, looked up by its primary key.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    @Override
    public void apply(SessionState state) {
        state.remove(record.id()); // locate and remove by primary key
    }

    /**
     * Reverses the deletion by re-adding the full record back to session state.
     *
     * Because the complete record was captured at construction time, this
     * restore is purely in-memory with no database involvement.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    @Override
    public void undo(SessionState state)  {
        state.add(record); // re-insert the original record — mirrors AddCommand.apply()
    }

    /**
     * Returns the record held by this command.
     *
     * Used by {@link com.strive.session.SessionManager#flush()} to retrieve
     * the record's {@code id} and issue the corresponding DELETE via the DAO layer.
     *
     * @return the record that was (or will be) deleted; never {@code null}
     */
    public T getRecord() { return record; }
}
