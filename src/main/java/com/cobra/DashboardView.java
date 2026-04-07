package com.cobra;

import com.cobra.types.Limit;
import com.cobra.types.Transaction;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;

public class DashboardView {

    @FXML private ListView<String> transactionListView;

    @FXML private TextField testAmountField;

    @FXML private TextField testCategoryField;

    @FXML private Label statusLabel;

    private Controller controller;

    public void setController(Controller controller) {
        this.controller = controller;
        DBModel dbModel = DBModel.getInstance();
        dbModel.addCacheListener(() -> refresh(dbModel.getTransactions(), dbModel.getLimits()));

        refresh(dbModel.getTransactions(), dbModel.getLimits());
    }

    @FXML
    private void onAddTransactionClicked() {
        // launch the humble dialog
        InsertDialog dialog = new InsertDialog();
        dialog.showAndWait().ifPresent(result -> {
            // dire the event to the controller
            controller.onInsertTransactionRequested(result.amount(), result.category());
        });
    }

    @FXML
    private void onTestSubmitClicked() {
        String amountText = testAmountField.getText().trim();
        String category = testCategoryField.getText().trim();

        if (amountText.isEmpty() || category.isEmpty()) {
            statusLabel.setText("Please fill in both fields.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);
            controller.onInsertTransactionRequested(amount, category);
            testAmountField.clear();
            testCategoryField.clear();
            statusLabel.setText("Transaction added.");
        } catch (NumberFormatException e) {
            statusLabel.setText("Amount must be a number.");
        }
    }

    @FXML
    private void onDeleteSelectedClicked() {
        int selectedIndex = transactionListView.getSelectionModel().getSelectedIndex();

        if (selectedIndex < 0) {
            statusLabel.setText("Select a transaction to delete.");
            return;
        }

        String selected = transactionListView.getSelectionModel().getSelectedItem();

        try {
            int id = Integer.parseInt(selected.split("\\|")[0].trim());
            controller.onDeleteTransactionRequested(id);
            statusLabel.setText("Transaction deleted.");
        } catch (NumberFormatException e) {
            statusLabel.setText("Could not parse transaction id.");
        }
    }

    @FXML
    private void onUndoClicked() {
        controller.onUndoRequested();
    }

    @FXML
    private void onExportClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Spending Report");
        fileChooser.setInitialFileName("strive_report.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(transactionListView.getScene().getWindow());

        if (file != null) {
            controller.onExportRequested(file.getAbsolutePath());
            statusLabel.setText("Exported to " + file.getName());
        }
    }

    public void refresh(ArrayList<Transaction> transactions, ArrayList<Limit> limits) {
        transactionListView.getItems().clear();

        for (Transaction t : transactions) {
            transactionListView.getItems().add(
                    String.format("%d | $%.2f | %s | %s", t.id(), t.amount(), t.category(), t.date()));
        }

        System.out.println("[DashboardView] Refreshed - " + transactions.size() + " transactions displayed.");
    }
}