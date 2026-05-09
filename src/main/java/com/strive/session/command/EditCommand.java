package com.strive.session.command;

import com.strive.model.DBRecord;
import com.strive.session.SessionState;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * A {@link Command} that replaces an existing {@link DBRecord} in the
 * in-memory session state with an updated version of itself.
 *
 * Because model records are immutable Java {@code record}s, an "edit" is
 * implemented as a full record replacement: the old record ({@code previous})
 * and the new record ({@code updated}) share the same {@code id} but differ
 * in one or more field values. {@link SessionState#update} locates the old
 * record by {@code id} and swaps it out.
 *
 * Undo: Both the previous and updated records are held in memory,
 * so a single-step undo is trivial — just call {@code state.update(previous)}
 * to swap back, with no database read required.
 *
 * Like {@link AddCommand} and {@link DeleteCommand}, this command is used
 * exclusively for {@link com.strive.model.Transaction} edits. Limit edits go
 * through {@link com.strive.session.SessionManager#applyPermanent} and never
 * reach the undo stack.
 *
 * @param <T> the type of record being edited; must implement {@link DBRecord}
 *            so {@link SessionState#update} can locate it by {@code id}
 *
 * @see AddCommand
 * @see DeleteCommand
 * @see com.strive.session.SessionManager#apply(Command)
 */

public class EditCommand<T extends DBRecord> implements Command {
    /** The record as it existed before the edit; retained so {@link #undo} can restore it. */
    private final T previous;
    /** The record with the user's new values applied; used by {@link #apply} and {@link #getUpdated}. */
    private final T updated;

    /**
     * Constructs an {@code EditCommand} capturing both the before and after states.
     *
     * {@code previous} and {@code updated} must share the same {@code id()}
     * value — this is what allows {@link SessionState#update} to find and
     * replace the correct record in either direction.
     *
     * @param previous the record in its current (pre-edit) state; must not be {@code null}
     * @param updated  the record with the user's new field values; must not be {@code null}
     */
    public EditCommand(T previous, T updated) {
        this.previous = previous; // snapshot of the record before changes
        this.updated  = updated; // snapshot of the record after changes
    }

    /**
     * Replaces the old record with the updated version in session state.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    @Override public void apply(SessionState state) {
        state.update(updated); // swap in the new record, matched by id
    }

    /**
     * Reverses the edit by restoring the previous version of the record in
     * session state.
     *
     * Because {@code previous} is already held in memory, this restore
     * requires no database read.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    @Override public void undo(SessionState state)  {
        state.update(previous); // swap the original record back in, matched by id
    }

    /**
     * Returns the updated record held by this command.
     *
     * Used by {@link com.strive.session.SessionManager#flush()} to retrieve
     * the new field values and issue the corresponding UPDATE via the DAO layer.
     *
     * @return the record with the user's new values; never {@code null}
     */
    public T getUpdated()  { return updated; }
}
