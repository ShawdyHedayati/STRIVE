package com.strive.session;

/**
 * Represents a single reversible user action in the session command log
 *
 * Every mutation a user makes (add, edit, delete) is wrapped in a
 * Command and passed to {@link SessionManager#apply(Command)}
 * Commands are held in memory and only written to the database when
 * {@link SessionManager#flush()} is explicitly called
 *
 * Each command knows how to apply itself, reverse itself, and produce
 * its own inverse so making a single step undo straightforward without
 * replaying the full log
 */

public interface Command {
    /**
     * Applies this command to the given in memory session state
     *
     * @param state the current session state to mutate
     */
    void apply(SessionState state);

    /**
     * Reverses this command's effect on the given session state
     * Called by {@link SessionManager#undo()} on the last command in the log
     *
     * @param state the current session state to mutate
     */
    void undo(SessionState state);
}
