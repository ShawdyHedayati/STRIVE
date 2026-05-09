package com.strive.config;

import com.strive.controller.LimitController;
import com.strive.controller.NavigationController;
import com.strive.controller.TransactionController;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Static singleton registry that holds the three backend controllers for the
 * lifetime of the application.
 *
 * Why this exists: JavaFX's {@code FXMLLoader} instantiates FXML
 * controllers via reflection when {@code navigateTo()} loads a new view.
 * Because CoBRA does not use a custom controller factory, there is no standard
 * injection point to hand each FXML controller a reference to the backend.
 * Instead, each view's {@code initialize()} method pulls its controller from
 * this registry via the static {@code get*()} accessors.
 *
 * Lifecycle: {@link #init(TransactionController, LimitController, NavigationController)}
 * is called exactly once by {@link com.strive.STRIVEApp#start} before any FXML
 * is loaded. After that point the three fields are effectively immutable for the
 * rest of the application's lifetime (no setter is exposed).
 *
 * Trade-off: Using static state couples all views to this global registry.
 * This is an acceptable trade-off for a single-user desktop application, but a
 * controller factory or dependency injection framework would be the preferred
 * approach in a larger codebase.
 *
 * @see com.strive.STRIVEApp#start(javafx.stage.Stage)
 */

public class AppContext {
    // Static fields — populated once at startup, read-only thereafter
    private static TransactionController transactionController;
    private static LimitController limitController;
    private static NavigationController navigationController;

    /**
     * Populates the registry with the three fully-constructed backend controllers.
     *
     * Must be called exactly once, by {@link com.strive.STRIVEApp#start},
     * before any FXML view is loaded. Calling this method a second time will
     * silently overwrite the existing references.
     *
     * @param tc the transaction controller; must not be {@code null}
     * @param lc the limit controller; must not be {@code null}
     * @param nc the navigation controller; must not be {@code null}
     */
    public static void init(TransactionController tc, LimitController lc, NavigationController nc) {
        transactionController = tc; // wires transaction CRUD to the session layer
        limitController = lc; // wires limit CRUD and weekly reset to the session layer
        navigationController = nc; // wires view-switching logic
    }

    /**
     * Returns the application-wide {@link TransactionController}.
     *
     * @return the transaction controller; {@code null} if {@link #init} has not been called yet
     */
    public static TransactionController getTransactionController() { return transactionController; }

    /**
     * Returns the application-wide {@link LimitController}.
     *
     * @return the limit controller; {@code null} if {@link #init} has not been called yet
     */
    public static LimitController getLimitController() { return limitController; }

    /**
     * Returns the application-wide {@link NavigationController}.
     *
     * @return the navigation controller; {@code null} if {@link #init} has not been called yet
     */
    public static NavigationController getNavigationController() { return navigationController; }
}
