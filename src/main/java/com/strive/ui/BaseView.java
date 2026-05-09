package com.strive.ui;

import com.strive.STRIVEApp;
import com.strive.controller.NavigationController;
import com.strive.session.SessionListener;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Abstract base class for all FXML-backed views in CoBRA.
 *
 * <p>Consolidates shared UI behavior so that {@link DashboardView} and
 * {@link ChartsView} don't duplicate it:
 *
 *   Implements {@link SessionListener} so subclasses automatically join
 *   the observer chain when registered via
 *   {@link com.strive.controller.TransactionController#addListener}.
 *   Implements {@link Initializable} so JavaFX calls {@code initialize()}
 *   after FXML injection, allowing subclasses to wire themselves to
 *   {@link com.strive.config.AppContext}.
 *   Provides shared dialog helpers ({@link #showError}, {@link #showInfo},
 *   {@link #initDialog}) so all modal dialogs in the app are styled and
 *   configured consistently.
 *   Provides {@link #syncSaveButton()} to keep the Save button's visual
 *   state in sync with the session's dirty flag.
 *
 * @see DashboardView
 * @see ChartsView
 */
public abstract class BaseView implements Initializable, SessionListener {
    /**
     * The Save button injected from the shared FXML layout.
     * Its style class is toggled between {@code btn-outline} (no unsaved changes)
     * and {@code btn-primary} (unsaved changes exist) by {@link #syncSaveButton()}.
     */
    @FXML protected Button saveBtn;

    /**
     * The navigation controller, set by each subclass in its {@code initialize()}
     * method via {@link com.strive.config.AppContext#getNavigationController()}.
     * Used by {@link #syncSaveButton()} to check the dirty state.
     */
    protected NavigationController navigationController;

    /**
     * Synchronizes the Save button's visual style with the session's dirty state.
     *
     * When the session has unsaved changes, the button switches to
     * {@code btn-primary} (solid, prominent) to draw the user's attention.
     * When all changes have been saved or discarded, it reverts to
     * {@code btn-outline} (subtle, de-emphasized).
     *
     * Guards against {@code null} in case the button has not yet been
     * injected or the navigation controller has not yet been wired — safe to
     * call early in the initialization sequence.
     */
    protected void syncSaveButton() {
        // Guard: skip if FXML injection or controller wiring hasn't happened yet
        if (saveBtn == null || navigationController == null) return;

        if (navigationController.hasUnsavedChanges()) {
            // Unsaved changes — promote button to primary (solid) style
            saveBtn.getStyleClass().removeAll("btn-outline");
            if (!saveBtn.getStyleClass().contains("btn-primary"))
                saveBtn.getStyleClass().add("btn-primary"); // avoid duplicate class entries
        } else {
            // No unsaved changes — demote button back to outline style
            saveBtn.getStyleClass().removeAll("btn-primary");
            if (!saveBtn.getStyleClass().contains("btn-outline"))
                saveBtn.getStyleClass().add("btn-outline"); // avoid duplicate class entries
        }
    }

    /**
     * Applies the application's shared stylesheet to a dialog's pane, ensuring
     * all modal dialogs render with consistent CoBRA styling.
     *
     * @param pane the {@link DialogPane} to style; must not be {@code null}
     */
    protected void applyStyles(DialogPane pane) {
        // Load styles.css from the classpath and apply it to the dialog pane
        pane.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/views/styles.css")).toExternalForm());
    }

    /**
     * Configures a {@link Dialog} with CoBRA's standard modal settings:
     * owned by the primary stage, window-modal, undecorated (no OS title bar),
     * and transparent background. Also suppresses the OS-level close request
     * on the dialog window, forcing the user to use the dialog's own controls.
     *
     * Must be called before {@link Dialog#showAndWait()} to ensure the
     * dialog is correctly positioned and styled. Called internally by
     * {@link #showError} and {@link #showInfo}.
     *
     * @param d the dialog to configure; must not be {@code null}
     */
    protected void initDialog(javafx.scene.control.Dialog<?> d) {
        d.initOwner(STRIVEApp.getPrimaryStage()); // anchor dialog to the main window
        d.initModality(Modality.WINDOW_MODAL); // block interaction with the parent window
        d.initStyle(StageStyle.UNDECORATED); // remove OS-native title bar and borders

        d.setOnShown(e -> {
            Stage s = (Stage) d.getDialogPane().getScene().getWindow();

            // Make the scene background transparent so the custom CSS border shows cleanly
            d.getDialogPane().getScene().setFill(Color.TRANSPARENT);

            // Suppress the OS close button (X) — the user must use the dialog's buttons
            s.setOnCloseRequest(Event::consume);
        });
    }

    /**
     * Displays a styled, modal error alert with the given title and message.
     *
     * Blocks until the user dismisses the dialog. The header text is
     * intentionally suppressed ({@code setHeaderText(null)}) to keep the
     * dialog compact — only the content text and title are shown.
     *
     * @param title   the dialog window title (e.g. {@code "Invalid Input"})
     * @param message the error message to display to the user
     */
    protected void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        initDialog(a); // apply standard modal configuration
        a.setTitle(title);
        a.setHeaderText(null); // suppress header — content text is sufficient
        a.setContentText(message);
        applyStyles(a.getDialogPane()); // apply CoBRA stylesheet
        a.showAndWait(); // block until dismissed
    }

    /**
     * Displays a styled, modal informational alert with the given title and message.
     *
     * Blocks until the user dismisses the dialog. Mirrors {@link #showError}
     * in structure — only the alert type differs.
     *
     * @param title   the dialog window title (e.g. {@code "Export Successful"})
     * @param message the informational message to display to the user
     */
    protected void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        initDialog(a); // apply standard modal configuration
        a.setTitle(title);
        a.setHeaderText(null); // suppress header — content text is sufficient
        a.setContentText(message);
        applyStyles(a.getDialogPane()); // apply CoBRA stylesheet
        a.showAndWait(); // block until dismissed
    }
}
