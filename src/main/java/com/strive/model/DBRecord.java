package com.strive.model;

/**
 * Marker interface for all db backed records
 * Any class that maps to a db row must implement this
 * so the session layer can identify records by their primary key
 */

public interface DBRecord {
    // returns the unique primary key for this record
    int id();
}
