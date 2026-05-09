package com.strive.session;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Observer interface for components that need to react when session state changes.
 *
 * CoBRA uses a lightweight <b>Observer pattern</b> to keep the UI in sync
 * with the session. UI views implement this interface and register themselves
 * with {@link SessionManager#addListener(SessionListener)}. Whenever the session
 * state changes — a command is applied, undone, flushed, or discarded —
 * {@link SessionManager} calls {@link #onSessionUpdated()} on every registered
 * listener, triggering a UI refresh.
 *
 * This decouples the session layer from the view layer: {@link SessionManager}
 * holds no direct references to any specific view class, and views need no
 * direct reference to each other. Adding a new view that reacts to state changes
 * only requires implementing this interface and registering with the session.
 *
 * Current implementors:
 * {@link com.strive.ui.DashboardView} — refreshes transaction table and summary
 * {@link com.strive.ui.ChartsView} — refreshes spending charts
 *
 * @see SessionManager#addListener(SessionListener)
 */

public interface SessionListener {
    /**
     * Called by {@link SessionManager} after any operation that changes session state,
     * including applying a command, undoing a command, flushing to the database,
     * or discarding pending changes.
     *
     * Implementations should re-query the session for fresh data and repopulate
     * their UI components. This method is always called on whichever thread
     * {@link SessionManager} is operating on — implementations that update JavaFX
     * nodes must ensure they do so on the JavaFX Application Thread
     * (e.g. via {@code Platform.runLater()} if needed).
     */
    void onSessionUpdated();
}
