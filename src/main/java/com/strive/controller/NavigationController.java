package com.strive.controller;

import com.strive.STRIVEApp;
import com.strive.session.SessionManager;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Manages navigation between the Dashboard and Charts views, and controls
 * the save/discard lifecycle for unsaved session changes.
 *
 * This controller is the sole entry point for flushing the session to
 * the database. No other controller or view calls {@link SessionManager#flush()}
 * directly — all save operations are routed through {@link #requestSave()} here.
 *
 * It also acts as the gatekeeper for application close: {@link STRIVEApp}
 * calls {@link #hasUnsavedChanges()} before allowing the window to close, and
 * shows an "exit without saving" warning dialog if unsaved changes exist.
 *
 * @see com.strive.config.AppContext
 * @see STRIVEApp
 * @see SessionManager#flush()
 */

public class NavigationController {
    /** The shared session manager; used to flush changes and check dirty state. */
    private final SessionManager session;

    /**
     * Constructs a {@code NavigationController} backed by the shared session manager.
     *
     * @param session the session manager shared across all controllers;
     *                must not be {@code null}
     */
    public NavigationController(SessionManager session) { this.session = session; }

    /**
     * Signals a navigation to the Dashboard view.
     *
     * The actual panel switching is handled by the UI layer
     * ({@link com.strive.ui.DashboardView} / {@link com.strive.ui.ChartsView}).
     * This method currently serves as a logging hook and extension point for
     * any future pre-navigation logic (e.g. saving scroll position).
     */
    public void goToDashboard() {
        // Placeholder for pre-navigation logic; UI handles the actual panel switch
        System.out.println("[NavigationController] Navigated to Dashboard.");
    }

    /**
     * Signals a navigation to the Charts view.
     *
     * The actual panel switching is handled by the UI layer
     * ({@link com.strive.ui.DashboardView} / {@link com.strive.ui.ChartsView}).
     * This method currently serves as a logging hook and extension point for
     * any future pre-navigation logic.
     */
    public void goToCharts() {
        // Placeholder for pre-navigation logic; UI handles the actual panel switch
        System.out.println("[NavigationController] Navigated to Charts.");
    }

    /**
     * Flushes all pending session commands to the database.
     *
     * This is the only way in-memory changes are persisted. The user
     * must explicitly trigger this (via the Save button) for changes to survive
     * the session — closing the application without saving discards all pending
     * commands.
     *
     * Delegates directly to {@link SessionManager#flush()}, which drains
     * both the permanent log and the undoable command log, issues the
     * corresponding SQL via the DAO layer, and resets the dirty flag.
     */
    public void requestSave() {
        session.flush(); // drain both command logs and write all pending changes to the DB
        System.out.println("[NavigationController] Changes saved.");
    }

    /**
     * Returns {@code true} if the session has uncommitted changes that have not
     * yet been flushed to the database.
     *
     * Called by {@link STRIVEApp} during the window-close sequence to decide
     * whether to show the "exit without saving" warning dialog. If this returns
     * {@code false}, the application closes immediately with no prompt.
     *
     * @return {@code true} if either command log contains unflushed commands;
     *         delegates to {@link SessionManager#isDirty()}
     */
    public boolean hasUnsavedChanges() {
        return session.isDirty(); // true if commandLog or permanentLog is non-empty
    }
}
