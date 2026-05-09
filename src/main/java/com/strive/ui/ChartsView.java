package com.strive.ui;

import com.strive.controller.NavigationController;
import com.strive.controller.TransactionController;
import com.strive.model.Transaction;
import com.strive.config.AppContext;
import com.strive.STRIVEApp;

import com.strive.util.CategoryRegistry;
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
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * JavaFX controller for the Charts view ({@code charts.fxml}).
 *
 * Terminology note: As with {@link DashboardView}, JavaFX calls this
 * a "controller" but in CoBRA's architecture it is the view — all
 * business logic and data mutations go through {@link TransactionController}.
 *
 * The Charts view contains three islands:
 *   All Transactions — full transaction history sorted by date
 *   descending, with inline edit and delete
 *   Week Comparison — Mon–Sun line chart overlaying this week's
 *   daily spending (blue) against the all-time daily average (grey)
 *   Average Spending by Category — bar chart showing the average
 *   transaction amount per category across all time
 *
 * Refresh lifecycle: Implements {@link com.strive.session.SessionListener}
 * (via {@link BaseView}) and registers itself during {@link #initialize}.
 * After any session state change, {@link #onSessionUpdated()} schedules
 * {@link #refresh()} on the JavaFX Application Thread.
 *
 * @see BaseView
 * @see DashboardView
 * @see AppContext
 */

public class ChartsView extends BaseView {
    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 1: All Transactions
    // -------------------------------------------------------------------------

    /** Table displaying all transactions across all time, sorted by date descending. */
    @FXML private TableView<Transaction> allTransactionsTable;
    /** Column displaying each transaction's spending category. */
    @FXML private TableColumn<Transaction, String> allCategoryCol;
    /** Column displaying each transaction's formatted dollar amount. */
    @FXML private TableColumn<Transaction, String> allAmountCol;
    /** Column displaying each transaction's date. */
    @FXML private TableColumn<Transaction, String> allDateCol;
    /** Edit button; enabled only when a row is selected in {@link #allTransactionsTable}. */
    @FXML private Button editAllTxBtn;
    /** Delete button; enabled only when a row is selected in {@link #allTransactionsTable}. */
    @FXML private Button deleteAllTxBtn;

    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 2: Week Comparison Line Chart
    // -------------------------------------------------------------------------

    /**
     * Mon–Sun line chart with two series:
     *
     *   "Current Week" (blue) — this week's daily spending averages
     *   "Total Average" (grey) — all-time daily averages across all weeks
     */
    @FXML private LineChart<String, Number> weekComparisonChart;

    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 3: Average Spending Bar Chart
    // -------------------------------------------------------------------------

    /**
     * Bar chart showing the average transaction amount per category across all time.
     * One bar per category that has at least one transaction; bars are coloured
     * using each category's {@link CategoryRegistry} colour.
     */
    @FXML private BarChart<String, Number> avgSpendingChart;

    // -------------------------------------------------------------------------
    // Backend controller references
    // -------------------------------------------------------------------------

    /** Handles transaction mutations and supplies display-ready chart data. */
    private TransactionController transactionController;

    // navigationController is inherited from BaseView
    private NavigationController navigationController;

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Called by JavaFX after all {@code @FXML} fields have been injected.
     * Wires backend controllers from {@link AppContext}, configures the
     * transaction table and chart appearance settings, registers this view
     * as a {@link com.strive.session.SessionListener}, and performs the
     * initial data load.
     *
     * @param url unused — required by {@link javafx.fxml.Initializable}
     * @param rb  unused — required by {@link javafx.fxml.Initializable}
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Fetch backend controllers from the static registry populated at startup
        transactionController = AppContext.getTransactionController();
        navigationController  = AppContext.getNavigationController();

        // Register as a session listener so onSessionUpdated() → refresh() fires automatically
        transactionController.addListener(this);

        // CONSTRAINED_FLEX_LAST_COLUMN prevents a horizontal scrollbar by making the
        // last column absorb any leftover width — same fix as DashboardView
        // issue with jfx -> debug through claude to come to this solution
        allTransactionsTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Wire table columns with lambda cell factories.
        // Java records don't expose JavaBean-style getters, so PropertyValueFactory
        // won't work — lambdas calling the record accessor methods are required.
        allCategoryCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().category()));
        allAmountCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", data.getValue().amount())));
        allDateCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().date().toString()));

        // Edit and delete buttons start disabled; enabled only when a row is selected
        editAllTxBtn.setDisable(true);
        deleteAllTxBtn.setDisable(true);
        allTransactionsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> {
                    boolean hasSel = newSel != null;
                    editAllTxBtn.setDisable(!hasSel);
                    deleteAllTxBtn.setDisable(!hasSel);
                });

        // Disable JavaFX's built-in chart animations — they conflict with the manual
        // color and hover wiring applied in Platform.runLater after each data load
        weekComparisonChart.setAnimated(false);
        avgSpendingChart.setAnimated(false);

        // The bar chart legend adds no value when bars are individually coloured;
        // hiding it keeps the chart compact
        avgSpendingChart.setLegendVisible(false);

        syncSaveButton(); // set initial save button visual state
        refresh(); // populate all three islands on first render
    }

    // -------------------------------------------------------------------------
    // SessionListener
    // -------------------------------------------------------------------------

    /**
     * Called by {@link com.strive.session.SessionManager} after any command is
     * applied, undone, flushed, or discarded. Schedules a full refresh on the
     * JavaFX Application Thread.
     */
    @Override
    public void onSessionUpdated() {
        // JavaFX UI updates must always run on the FX Application Thread
        Platform.runLater(this::refresh);
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * Handles the Dashboard navigation button click. Notifies the navigation
     * controller and switches the active FXML view. Shows an error dialog if
     * the Dashboard FXML cannot be loaded.
     */
    @FXML
    private void navigateToDashboard() {
        navigationController.goToDashboard();
        try {
            STRIVEApp.navigateTo("dashboard");
        } catch (IOException e) {
            showError("Navigation Error", "Could not open Dashboard: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Island 1 handlers — All Transactions
    // -------------------------------------------------------------------------

    /**
     * Handles the Edit button click on the All Transactions table. Opens a
     * modal dialog pre-populated with the selected transaction's current values.
     * Validates the new amount inline before allowing the dialog to close.
     *
     * On confirmation, delegates to {@link TransactionController#editTransaction}.
     * Refresh fires automatically via {@link #onSessionUpdated()}.
     */
    @FXML
    private void handleEditAllTransaction() {
        Transaction selected = allTransactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return; // guard: button should already be disabled when no selection

        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Edit Transaction");
        dialog.setHeaderText("Update transaction details");

        // Pre-populate fields with the selected transaction's current values
        ComboBox<String> catCombo = new ComboBox<>();
        CategoryRegistry.getAll()
                .forEach(c -> catCombo.getItems().add(c.displayName()));
        catCombo.setValue(selected.category());

        TextField amtField = new TextField(String.valueOf(selected.amount()));
        javafx.scene.control.DatePicker dp = new javafx.scene.control.DatePicker(selected.date());

        // Inline error label; hidden until validation fails
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

        // Event filter on OK: validate amount before allowing the dialog to close
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                double amt = Double.parseDouble(amtField.getText().trim());
                if (amt <= 0) throw new NumberFormatException(); // treat non-positive as invalid
                errorLabel.setVisible(false); // clear any previous error
            } catch (NumberFormatException e) {
                errorLabel.setText("Please enter a valid amount greater than 0.");
                errorLabel.setVisible(true);
                event.consume(); // prevent dialog from closing on invalid input
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

    /**
     * Handles the Delete button click on the All Transactions table. Shows a
     * confirmation dialog, then delegates to
     * {@link TransactionController#deleteTransaction} if confirmed.
     *
     * Refresh fires automatically via {@link #onSessionUpdated()}.
     */
    @FXML
    private void handleDeleteAllTransaction() {
        Transaction selected = allTransactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return; // guard: button should already be disabled when no selection

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

    // -------------------------------------------------------------------------
    // Export handler
    // -------------------------------------------------------------------------

    /**
     * Handles the Export CSV button click. Opens a native save-file dialog
     * defaulting to {@code strive_transactions.csv}, then delegates to
     * {@link TransactionController#exportCSV} to write the file.
     *
     * Shows an info dialog on success confirming the save path.
     * If the user cancels the file chooser, the method returns silently.
     */
    @FXML
    private void handleExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transactions as CSV");
        chooser.setInitialFileName("strive_transactions.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(STRIVEApp.getPrimaryStage());
        if (file == null) return; // user canceled the file chooser — nothing to do

        // TransactionController handles file I/O; CSVExporter (BLL) builds the string
        transactionController.exportCSV(file.getAbsolutePath());
        showInfo("Export Complete",
                "Transactions saved to:\n" + file.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Footer handler — Save
    // -------------------------------------------------------------------------

    /**
     * Handles the Save button click. Flushes all pending session commands to
     * the database and syncs the save button's visual state.
     */
    @FXML
    private void handleSave() {
        navigationController.requestSave(); // flush both command logs to the DB
        syncSaveButton(); // update button appearance immediately
    }

    // -------------------------------------------------------------------------
    // Refresh helpers
    // -------------------------------------------------------------------------

    /**
     * Performs a full refresh of all three chart islands and syncs the save
     * button. Called after every session state change via {@link #onSessionUpdated()}.
     */
    private void refresh() {
        refreshAllTransactions();
        refreshWeekComparison();
        refreshAvgSpending();
        syncSaveButton();
    }

    /**
     * Repopulates the All Transactions table with every transaction in the
     * session, sorted by date descending (most recent first) per the spec's
     * "ordered by date priority" requirement.
     *
     * Delegates to {@link TransactionController#getAllTransactions()}, which
     * itself delegates to {@link com.strive.bll.ChartCalculator#allTransactionsSortedByDate}.
     */
    private void refreshAllTransactions() {
        allTransactionsTable.getItems().setAll(
                transactionController.getAllTransactions());
    }

    /**
     * Rebuilds the Mon–Sun week comparison line chart with two data series:
     *
     *   "Current Week" (blue, {@code #3B82F6}) — daily spending
     *   averages for the current Mon–Sun window
     *   "Total Average" (grey, {@code #9CA3AF}) — all-time daily
     *   spending averages across every week in the session
     *
     * Series colors and hover tooltips are applied inside a deferred
     * {@code Platform.runLater} call because chart nodes are not available
     * in the scene graph until JavaFX has completed its layout pass after
     * the data is added.
     *
     * Both series always contain all 7 days (Mon–Sun), with {@code 0.0}
     * for days that have no transactions — this guarantees a complete x-axis
     * with no missing points.
     */
    private void refreshWeekComparison() {
        weekComparisonChart.getData().clear();

        // Fetch both datasets from the BLL via the controller
        Map<DayOfWeek, Double> currentWeek = transactionController.getWeekOverlayData(true);
        Map<DayOfWeek, Double> totalAvg    = transactionController.getWeekOverlayData(false);

        XYChart.Series<String, Number> currentSeries = new XYChart.Series<>();
        currentSeries.setName("Current Week");

        XYChart.Series<String, Number> avgSeries = new XYChart.Series<>();
        avgSeries.setName("Total Average");

        // DayOfWeek.values() order: MONDAY=1 … SUNDAY=7; labels array mirrors this order
        String[] labels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        DayOfWeek[] days = DayOfWeek.values();

        for (int i = 0; i < days.length; i++) {
            String label = labels[i];
            // getOrDefault(0.0) handles days with no transactions — keeps the x-axis complete
            currentSeries.getData().add(new XYChart.Data<>(
                    label, currentWeek.getOrDefault(days[i], 0.0)));
            avgSeries.getData().add(new XYChart.Data<>(
                    label, totalAvg.getOrDefault(days[i], 0.0)));
        }

        weekComparisonChart.getData().addAll(currentSeries, avgSeries);

        // Defer colour and hover wiring until JavaFX has finished rendering the chart nodes
        Platform.runLater(() -> {
            // Apply series line colours via inline CSS
            if (currentSeries.getNode() != null)
                currentSeries.getNode().setStyle("-fx-stroke: #3B82F6;"); // blue for current week
            if (avgSeries.getNode() != null)
                avgSeries.getNode().setStyle("-fx-stroke: #9CA3AF;"); // grey for total average

            // Shared popup for both series' hover tooltips
            Popup linePopup = new Popup();
            Label lineLabel = new Label();
            lineLabel.getStyleClass().add("chart-hover-label");
            linePopup.getContent().add(lineLabel);

            // Wire hover tooltips for the current-week data points
            for (XYChart.Data<String, Number> point : currentSeries.getData()) {
                if (point.getNode() != null) {
                    point.getNode().setOnMouseMoved(e -> {
                        lineLabel.setText(String.format("Current Week — %s: $%.2f",
                                point.getXValue(), point.getYValue().doubleValue()));
                        linePopup.show(STRIVEApp.getPrimaryStage(),
                                e.getScreenX() + 14, e.getScreenY() + 14); // offset avoids cursor overlap
                    });
                    point.getNode().setOnMouseExited(e -> linePopup.hide());
                }
            }

            // Wire hover tooltips for the total-average data points
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
     * Rebuilds the average spending bar chart with one bar per category that
     * has at least one transaction. Bars with a zero average are excluded to
     * keep the chart uncluttered.
     *
     * Each bar is coloured using its category's canonical colour from
     * {@link CategoryRegistry#colorFor(String)} and wired with a hover tooltip
     * showing the category name and average amount.
     *
     * Bar colour and hover wiring are applied inside a per-bar
     * {@code Platform.runLater} because each bar node is not guaranteed to
     * exist in the scene graph until after JavaFX processes the data addition.
     */
    private void refreshAvgSpending() {
        avgSpendingChart.getData().clear();

        Map<String, Double> avgData = transactionController.getAvgBarGraphData();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Average Spending"); // series name hidden since legend is disabled

        // Shared popup for all bar hover tooltips
        Popup barPopup = new Popup();
        Label barLabel = new Label();
        barLabel.getStyleClass().add("chart-hover-label");
        barPopup.getContent().add(barLabel);

        avgData.forEach((cat, avg) -> {
            if (avg > 0) { // skip categories with no transactions (avg = 0.0)
                String color = CategoryRegistry.colorFor(cat);
                XYChart.Data<String, Number> bar = new XYChart.Data<>(cat, avg);
                series.getData().add(bar);

                // Defer per-bar colour and hover wiring until the node is in the scene graph
                Platform.runLater(() -> {
                    if (bar.getNode() != null) {
                        bar.getNode().setStyle("-fx-bar-fill: " + color + ";"); // apply category colour
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
