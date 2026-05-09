package com.strive.session.command;

import com.strive.model.DBRecord;
import com.strive.session.SessionState;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * A {@link Command} that adds a new {@link DBRecord} to the in-memory session state.
 *
 * When applied, the record is appended to the appropriate list inside
 * {@link SessionState}. When undone, it is removed by its primary key —
 * restoring the state to exactly what it was before the add.
 *
 * This command is used exclusively for {@link com.strive.model.Transaction}
 * additions. Limit mutations use {@link com.strive.session.SessionManager#applyPermanent}
 * and therefore never land on the undo stack.
 *
 * @param <T> the type of record being added; must implement {@link DBRecord}
 *            so the undo step can look it up by {@code id}
 *
 * @see EditCommand
 * @see DeleteCommand
 * @see com.strive.session.SessionManager#apply(Command)
 */

public class AddCommand<T extends DBRecord> implements Command {
    /** The record to be added to session state; captured at construction time and never mutated. */
    private final T record;

    /**
     * Constructs an {@code AddCommand} for the given record.
     *
     * The record should already have its {@code id} assigned before being
     * wrapped in this command, since {@link #undo} uses {@code id()} to locate
     * and remove it.
     *
     * @param record the record to add; must not be {@code null}
     */
    public AddCommand(T record) { this.record = record; }

    /**
     * Adds the record to the session state.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    @Override
    public void apply(SessionState state) {
        state.add(record); // appends record to the appropriate in-memory list
    }

    /**
     * Reverses the add by removing the record from session state, looked up
     * by its primary key.
     *
     * @param state the live session state to mutate; must not be {@code null}
     */
    @Override public void undo(SessionState state)  {
        state.remove(record.id()); // remove by id — mirrors the apply() add
    }

    /**
     * Returns the record held by this command.
     *
     * Used by {@link com.strive.session.SessionManager#flush()} to retrieve
     * the record and issue the corresponding INSERT via the DAO layer.
     *
     * @return the record that was (or will be) added; never {@code null}
     */
    public T getRecord() { return record; }
}
