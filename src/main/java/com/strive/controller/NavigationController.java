package com.strive.controller;

import com.strive.STRIVEApp;
import com.strive.session.SessionManager;

/**
 * Manages nav between dash and charts views, and controls the save/discard
 * lifecycle for unsaved session changes
 *
 * This controller is the only entry point for flushing the session to
 * the db. It is also responsible for checking whether unsaved changes
 * exists before allowing the application to close, triggers the "exit
 * wihout saving" warning dialog in {@link STRIVEApp}
 */

public class NavigationController {
    // represents the two top level views the user can nav between
    public enum View { DASHBOARD, CHARTS }

    private final SessionManager session;

    // tracks which view is currently active; defaults to dash on startup
    private View currentView = View.DASHBOARD;

    /**
     * Contructs a NavigationController nacked by the shared session manager
     *
     * @param session the session manager shared across all controllers
     */
    public NavigationController(SessionManager session) { this.session = session; }

    /**
     * Returns the currently active view
     *
     * @return the current view
     */
    public View getCurrentView() { return currentView; }

    /**
     * Switches the active view to the Dash
     * The UI layer is responsible for showing/hiding the appropriate panels
     */
    public void goToDashboard() {
        currentView = View.DASHBOARD;
        System.out.println("[NavigationController] Navigated to Dashboard.");
    }

    /**
     * Switches the active view to Charts
     * The UI layer is responsible for showing/hiding the appropriate panels
     */
    public void goToCharts() {
        currentView = View.CHARTS;
        System.out.println("[NavigationController] Navigated to Charts.");
    }

    /**
     * Flushes all pending session commands to the sb
     * This is the only way data is persisted; the user must call this
     * explicitly (via the save button) for changes to survive the session
     */
    public void requestSave() {
        session.flush();
        System.out.println("[NavigationController] Changes saved.");
    }

    /**
     * Returns {@code true} if there are uncommitted changes in the sesh
     * Used by {@link STRIVEApp} to decide whether to show the
     * "exit without saving" dialog.
     *
     * @return true if the session has unsaved changes
     */
    public boolean hasUnsavedChanges() { return session.isDirty(); }
}
