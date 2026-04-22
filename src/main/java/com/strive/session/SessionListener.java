package com.strive.session;

/**
 * Observer interface for components that need to react when session state changes
 *
 * UI views implement this interface and register themselves with
 * {@link SessionManager#addListener(SessionListener)} so they
 * automatically refresh whenever a command is applied or undo so
 * without needed a direct reference to the session or any controller
 */

public interface SessionListener {
    /**
     * Called by {@link SessionManager} after any command is applied,
     * undone, flushed, or discarded
     */
    void onSessionUpdated();
}
