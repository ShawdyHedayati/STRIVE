package com.cobra;

import com.cobra.types.Statement;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class DashboardView {

    private Controller controller;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    @FXML
    private void onAddTransactionClicked() {
        // launch the humble dialog
        InsertDialog dialog = new InsertDialog();
        dialog.showAndWait().ifPresent(result -> {
            // dire the event to the controller
            controller.onInsertRequested(result.amount(), result.category());
        });
    }
}