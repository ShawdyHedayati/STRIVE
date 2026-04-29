package com.strive.ui;

import com.strive.controller.NavigationController;
import com.strive.controller.TransactionController;
import com.strive.model.Transaction;
import com.strive.AppContext;
import com.strive.STRIVEApp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * JavaFX controller for charts.fxml
 *
 * Implements {@link SessionListener} so it refreshes automatically
 * whenever any backend command is applied, undone, or flushed
 *
 * Backed controllers are fetched from {@link AppContext}
 */

// TODO: hover over for charts

public class ChartsView extends BaseView {

    // ISLAND 1 : all transactions
    @FXML private TableView<Transaction> allTransactionsTable;
    @FXML private TableColumn<Transaction, String> allCategoryCol;
    @FXML private TableColumn<Transaction, String> allAmountCol;
    @FXML private TableColumn<Transaction, String> allDateCol;
    @FXML private Button editAllTxBtn;
    @FXML private Button deleteAllTxBtn;

    // ISLAND 2 : this week's comparison (line chart)
    // mon-sun overlay: current week (blue) vs all time avg (grey)
    @FXML private LineChart<String, Number> weekComparisonChart;

    // ISLAND 3 : avg spending by category (bar chart)
    @FXML private BarChart<String, Number> avgSpendingChart;

    // BACKEND REFS
    private TransactionController transactionController;
    private NavigationController navigationController;

    // INIT

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        transactionController = AppContext.getTransactionController();
        navigationController  = AppContext.getNavigationController();

        // register as session listener
        transactionController.addListener(this);

        // issue with jfx -> debug through claude to come to this solution
        allTransactionsTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // wire table columns with lambda factories (records, not JavaBeans)
        allCategoryCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().category()));
        allAmountCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", data.getValue().amount())));
        allDateCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().date().toString()));

        // disable edit/delete until a row is selected
        editAllTxBtn.setDisable(true);
        deleteAllTxBtn.setDisable(true);
        allTransactionsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> {
                    boolean hasSel = newSel != null;
                    editAllTxBtn.setDisable(!hasSel);
                    deleteAllTxBtn.setDisable(!hasSel);
                });

        // chart appearance
        weekComparisonChart.setAnimated(false);
        avgSpendingChart.setAnimated(false);
        avgSpendingChart.setLegendVisible(false);

        syncSaveButton();
        refresh();
    }

    // SESSION LISTENER

    @Override
    public void onSessionUpdated() { Platform.runLater(this::refresh); }

    // NAV

    @FXML
    private void navigateToDashboard() {
        navigationController.goToDashboard();
        try {
            STRIVEApp.navigateTo("dashboard");
        } catch (IOException e) {
            showError("Navigation Error", "Could not open Dashboard: " + e.getMessage());
        }
    }

    // ALL TRANSACTIONS : handlers

    @FXML
    private void handleEditAllTransaction() {
        Transaction selected = allTransactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Edit Transaction");
        dialog.setHeaderText("Update transaction details");

        ComboBox<String> catCombo = new ComboBox<>();
        com.strive.model.CategoryRegistry.getAll()
                .forEach(c -> catCombo.getItems().add(c.displayName()));
        catCombo.setValue(selected.category());

        TextField amtField = new TextField(String.valueOf(selected.amount()));
        javafx.scene.control.DatePicker dp = new javafx.scene.control.DatePicker(selected.date());

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setVisible(false);

        VBox content = new VBox(8,
                new Label("Category:"), catCombo,
                new Label("Amount:"),   amtField,
                new Label("Date:"),     dp,
                errorLabel);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStyles(dialog.getDialogPane());

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                double amt = Double.parseDouble(amtField.getText().trim());
                if (amt <= 0) throw new NumberFormatException();
                errorLabel.setVisible(false);
            } catch (NumberFormatException e) {
                errorLabel.setText("Please enter a valid amount greater than 0.");
                errorLabel.setVisible(true);
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            double newAmount = Double.parseDouble(amtField.getText().trim());
            transactionController.editTransaction(
                    selected.id(), newAmount, catCombo.getValue(), dp.getValue());
            // refresh() fires automatically via onSessionUpdated()
        }
    }

    @FXML
    private void handleDeleteAllTransaction() {
        Transaction selected = allTransactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        initDialog(confirm);
        confirm.setTitle("Delete Transaction");
        confirm.setHeaderText("Are you sure?");
        confirm.setContentText("This transaction will be permanently removed.");
        applyStyles(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                transactionController.deleteTransaction(selected.id());
                // refresh() fires automatically via onSessionUpdated()
            }
        });
    }

    // EXPORT CSV

    @FXML
    private void handleExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transactions as CSV");
        chooser.setInitialFileName("strive_transactions.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(STRIVEApp.getPrimaryStage());
        if (file == null) return; // user cancelled

        // transactionController handles I/O; CSVExporter (BLL) builds the string
        transactionController.exportCSV(file.getAbsolutePath());
        showInfo("Export Complete",
                "Transactions saved to:\n" + file.getAbsolutePath());
    }

    // FOOTER : save

    @FXML
    private void handleSave() {
        navigationController.requestSave();
        syncSaveButton();
    }

    // REFRESH HELPERS

    // full refresh of all three chart islands
    private void refresh() {
        refreshAllTransactions();
        refreshWeekComparison();
        refreshAvgSpending();
        syncSaveButton();
    }

    /**
     * Populates All Transactions table, sort by date desc per spec
     * "ordered by date priority"
     */
    private void refreshAllTransactions() {
        allTransactionsTable.getItems().setAll(
                transactionController.getAllTransactions());
    }

    /**
     * Rebuild mon-sun linke chart with two series:
     * "Current Week" - (blue) this week's daily spending avg
     * "Total Average" - (grey) all time daily avgs
     */
    private void refreshWeekComparison() {
        weekComparisonChart.getData().clear();

        Map<DayOfWeek, Double> currentWeek = transactionController.getWeekOverlayData(true);
        Map<DayOfWeek, Double> totalAvg    = transactionController.getWeekOverlayData(false);

        XYChart.Series<String, Number> currentSeries = new XYChart.Series<>();
        currentSeries.setName("Current Week");

        XYChart.Series<String, Number> avgSeries = new XYChart.Series<>();
        avgSeries.setName("Total Average");

        // DayOfWeek enum order: MON=1 … SUN=7
        String[] labels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        DayOfWeek[] days = DayOfWeek.values();

        for (int i = 0; i < days.length; i++) {
            String label = labels[i];
            currentSeries.getData().add(new XYChart.Data<>(
                    label, currentWeek.getOrDefault(days[i], 0.0)));
            avgSeries.getData().add(new XYChart.Data<>(
                    label, totalAvg.getOrDefault(days[i], 0.0)));
        }

        weekComparisonChart.getData().addAll(currentSeries, avgSeries);

        // apply line colors after rendering (must run on next pulse)
        Platform.runLater(() -> {
            if (currentSeries.getNode() != null)
                currentSeries.getNode().setStyle("-fx-stroke: #3B82F6;"); // blue
            if (avgSeries.getNode() != null)
                avgSeries.getNode().setStyle("-fx-stroke: #9CA3AF;");     // grey

            Popup linePopup = new Popup();
            Label lineLabel = new Label();
            lineLabel.getStyleClass().add("chart-hover-label");
            linePopup.getContent().add(lineLabel);

            for (XYChart.Data<String, Number> point : currentSeries.getData()) {
                if (point.getNode() != null) {
                    point.getNode().setOnMouseMoved(e -> {
                        lineLabel.setText(String.format("Current Week — %s: $%.2f",
                                point.getXValue(), point.getYValue().doubleValue()));
                        linePopup.show(STRIVEApp.getPrimaryStage(),
                                e.getScreenX() + 14, e.getScreenY() + 14);
                    });
                    point.getNode().setOnMouseExited(e -> linePopup.hide());
                }
            }
            for (XYChart.Data<String, Number> point : avgSeries.getData()) {
                if (point.getNode() != null) {
                    point.getNode().setOnMouseMoved(e -> {
                        lineLabel.setText(String.format("Total Average — %s: $%.2f",
                                point.getXValue(), point.getYValue().doubleValue()));
                        linePopup.show(STRIVEApp.getPrimaryStage(),
                                e.getScreenX() + 14, e.getScreenY() + 14);
                    });
                    point.getNode().setOnMouseExited(e -> linePopup.hide());
                }
            }
        });
    }

    /**
     * Rebuild avg spending chart
     * Bars appear only for cat that have at least one transaction.
     * Colors match the CategoryRegistry palette
     */
    private void refreshAvgSpending() {
        avgSpendingChart.getData().clear();

        Map<String, Double> avgData = transactionController.getAvgBarGraphData();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Average Spending");

        Popup barPopup = new Popup();
        Label barLabel = new Label();
        barLabel.getStyleClass().add("chart-hover-label");
        barPopup.getContent().add(barLabel);

        avgData.forEach((cat, avg) -> {
            if (avg > 0) {
                String color = com.strive.model.CategoryRegistry.colorFor(cat);
                XYChart.Data<String, Number> bar = new XYChart.Data<>(cat, avg);
                series.getData().add(bar);

                Platform.runLater(() -> {
                    if (bar.getNode() != null) {
                        bar.getNode().setStyle("-fx-bar-fill: " + color + ";");
                        bar.getNode().setOnMouseMoved(e -> {
                            barLabel.setText(String.format("%s: $%.2f avg", cat, avg));
                            barPopup.show(STRIVEApp.getPrimaryStage(),
                                    e.getScreenX() + 14, e.getScreenY() + 14);
                        });
                        bar.getNode().setOnMouseExited(e -> barPopup.hide());
                    }
                });
            }
        });

        avgSpendingChart.getData().add(series);
    }
}
