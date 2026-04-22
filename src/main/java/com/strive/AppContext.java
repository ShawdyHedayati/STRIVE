package com.strive;

import com.strive.controller.LimitController;
import com.strive.controller.NavigationController;
import com.strive.controller.TransactionController;

/**
 * Static singleton that holds the three backend controllers
 *
 * Because STIVEApp.navigateTo() loads FXML without a controller factory,
 * each FXML controller self-wires in initialize() by calling AppContext.get*()
 * AppContext is populated once at application startup in STRIVEapp.start().
 */

public class AppContext {
    private static TransactionController transactionController;
    private static LimitController limitController;
    private static NavigationController navigationController;

    // called once by STRIVEApp before any FXML is loaded
    public static void init(TransactionController tc, LimitController lc, NavigationController nc) {
        transactionController = tc;
        limitController = lc;
        navigationController = nc;
    }

    public static TransactionController getTransactionController() { return transactionController; }

    public static LimitController getLimitController() { return limitController; }

    public static NavigationController getNavigationController() { return navigationController; }
}
