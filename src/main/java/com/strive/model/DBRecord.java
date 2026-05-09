package com.strive.model;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Marker interface for all database-backed model records.
 *
 * Any class that maps to a database row must implement this interface
 * so the session layer can uniformly identify and reference records
 * by their integer primary key — for example, when pushing commands
 * onto the undo stack or flushing changes to the database.
 *
 * @see com.strive.session.SessionManager
 */

public interface DBRecord {
    /**
     * Returns the unique integer primary key for this record,
     * corresponding to the {@code id} column in the backing database table.
     *
     * @return the record's primary key; {@code 0} or negative values indicate
     *         a record that has not yet been persisted
     */
    int id();
}
