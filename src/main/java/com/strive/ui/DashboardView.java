package com.strive.ui;

import com.strive.bll.LimitCalculator;
import com.strive.bll.SpendingCalculator;
import com.strive.controller.LimitController;
import com.strive.controller.TransactionController;
import com.strive.util.CategoryRegistry;
import com.strive.model.SpendingLimit;
import com.strive.model.Transaction;
import com.strive.session.SessionListener;
import com.strive.config.AppContext;
import com.strive.STRIVEApp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

/**
 * JavaFX "controller" for the Dashboard view (dashboard.fxml)
 * Controller in this case is per JFX terminology, this is still a view
 *
 * Implements {@link SessionListener} so it auto refreshes whenever the
 * session state changes, without needing to poll or hold a direct reference to the session
 *
 * Backend controllers are fetched from {@link AppContext} which is populated
 * by {@link STRIVEApp} before any fxml is loaded
 */

// TODO: hover over for charts

public class DashboardView extends BaseView {
    // ISLAND 1 - this weeks spending breakdown
    @FXML private PieChart spendingPieChart;
    private boolean pieAnimatedOnce = false;
    private boolean animatePieOnNextRefresh = false;
    // ISLAND 2 - spending limits
    // dynamic contrainer; limits cards added/removed here at runtime
    @FXML private VBox limitsContainer;
    @FXML private Label noSpendingDataLabel;
    @FXML private Label noLimitsDataLabel;
    // ISLAND 3 - enter transaction
    @FXML private ComboBox<String> txCategoryCombo;
    @FXML private TextField txAmountField;
    // shows "Limit: $0.00 remaining" or "Over limit by $0.00"
    @FXML private Label txLimitStatusLabel;
    @FXML private DatePicker txDatePicker;
    @FXML private Button undoBtn;

    // ISLAND 4 - todays entries
    @FXML private TableView<Transaction> todaysEntriesTable;
    @FXML private TableColumn<Transaction, String> todaysCategoryCol;
    @FXML private TableColumn<Transaction, String> todaysAmountCol;
    @FXML private TableColumn<Transaction, String> todaysDateCol;
    @FXML private Button editTxBtn;
    @FXML private Button deleteTxBtn;

    // handles all transaction related actions and supplies display data
    private TransactionController transactionController;

    // handles limit actions
    private LimitController limitController;

    private List<SpendingCalculator.PieSlice> lastPieSlices = List.of();

    // INIT
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // get backend controllers from static bridge
        transactionController = AppContext.getTransactionController();
        limitController = AppContext.getLimitController();
        navigationController = AppContext.getNavigationController();

        // issue with jfx -> debug through claude to come to this solution
        todaysEntriesTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // reg as sesh listener so refresh() fires on every state change
        transactionController.addListener(this);

        // populate cat dropdown from CategoryRegistry (single source)
        CategoryRegistry.getAll().forEach(c ->
                txCategoryCombo.getItems().add(c.displayName()));
        txCategoryCombo.getSelectionModel().selectFirst();

        // default date to today
        txDatePicker.setValue(LocalDate.now());

        // refresh lim status label when category selection changes
        txCategoryCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> refreshLimitStatusLabel(newVal));

        // Wire table columns with lambda cell fact
        // records do not have JavaBean getters so PropertyValueFactory will not work
        todaysCategoryCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().category()));
        todaysAmountCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", data.getValue().amount())));
        todaysDateCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().date().toString()));

        // disable edit/delete until a row is selected
        editTxBtn.setDisable(true);
        deleteTxBtn.setDisable(true);
        todaysEntriesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> {
                    boolean hasSel = newSel != null;
                    editTxBtn.setDisable(!hasSel);
                    deleteTxBtn.setDisable(!hasSel);
                });

        // sync save button visual state with session dirty flag
        syncSaveButton();

        // initial data load
        refresh();
    }

    // SESSION LISTENER

    /**
     * Called by SessionManager after any command is applied, undone,
     * flushed, or discarded. Triggers a full dashboard refresh.
     */
    @Override
    public void onSessionUpdated() {
        // JFX UI updates must run on the FX Application Thread
        Platform.runLater(this::refresh);
    }

    // NAVIGATION
    @FXML
    private void navigateToCharts() {
        navigationController.goToCharts();
        try {
            STRIVEApp.navigateTo("charts");
        } catch (IOException e) {
            showError("Navigation Error", "Could not open Charts view: " + e.getMessage());
        }
    }

    // ENTER TRANSACTION : handlers

    @FXML
    private void handleAddTransaction() {
        String category = txCategoryCombo.getValue();
        String amountText = txAmountField.getText().trim();
        LocalDate date = txDatePicker.getValue();

        if (category == null || amountText.isEmpty()) {
            showError("Validation Error", "Please select a category and enter an amount.");

            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(amountText);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Invalid Amount", "Please enter a positive number (e.g. 12.50).");

            return;
        }

        // delegate to controller -> session -> in mem state (not db yet)
        animatePieOnNextRefresh = true;
        transactionController.addTransaction(amount, category, date);
        // reset from fields
        txAmountField.clear();
        txDatePicker.setValue(LocalDate.now());

        // refresh() fires automatically via onSessionUpdated
    }

    private void syncUndoButton() {
        if (undoBtn == null) return;
        undoBtn.setDisable(
                !AppContext.getTransactionController().canUndo());
    }

    @FXML
    private void handleUndo() {

        animatePieOnNextRefresh = true;

        transactionController.undo();
    }

    // TODAY'S ENTRIES : handlers

    @FXML
    private void handleEditTransaction() {
        Transaction selected = todaysEntriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Edit Transaction");
        dialog.setHeaderText("Update transaction details");

        ComboBox<String> catCombo = new ComboBox<>();
        CategoryRegistry.getAll().forEach(c -> catCombo.getItems().add(c.displayName()));
        catCombo.setValue(selected.category());

        TextField amtField = new TextField(String.valueOf(selected.amount()));

        DatePicker dp = new DatePicker(selected.date());

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
            animatePieOnNextRefresh = true;
            transactionController.editTransaction(
                    selected.id(),
                    newAmount,
                    catCombo.getValue(),
                    dp.getValue());
            // refresh() fires automatically via onSessionUpdated()
        }
    }

    @FXML
    private void handleDeleteTransaction() {
        Transaction selected = todaysEntriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        initDialog(confirm);
        confirm.setTitle("Delete Transaction");
        confirm.setHeaderText("Are you sure?");
        confirm.setContentText("This transaction will be removed.");
        applyStyles(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                animatePieOnNextRefresh = true;
                transactionController.deleteTransaction(selected.id());
                // refresh() fires automatically via onSessionUpdated()
            }
        });
    }

    // SPENDING LIMIT : handlers

    @FXML
    private void handleAddLimit() {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Add Spending Limit");
        dialog.setHeaderText("Set a weekly spending limit for a category");

        ComboBox<String> catCombo = new ComboBox<>();
        CategoryRegistry.getAll().forEach(c -> catCombo.getItems().add(c.displayName()));
        catCombo.getSelectionModel().selectFirst();

        TextField amtField = new TextField();
        amtField.setPromptText("0.00");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setVisible(false);

        VBox content = new VBox(8,
                new Label("Category:"), catCombo,
                new Label("Amount:"),   amtField,
                errorLabel);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStyles(dialog.getDialogPane());

        // prevent close on OK if category already has a limit
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String cat = catCombo.getValue();
            String amtText = amtField.getText().trim();
            double amt = -1;
            try { amt = Double.parseDouble(amtText); } catch (NumberFormatException ignored) {}

            if (amt <= 0) {
                errorLabel.setText("Please enter a valid amount greater than 0.");
                errorLabel.setVisible(true);
                event.consume();
            } else if (limitController.categoryLimitExists(cat)) {
                errorLabel.setText("A limit for " + cat +
                        " already exists, please select that limit to edit.");
                errorLabel.setVisible(true);
                event.consume();
            } else {
                errorLabel.setVisible(false);
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                double amt = Double.parseDouble(amtField.getText().trim());
                limitController.addLimit(catCombo.getValue(), amt);
            }
        });
    }

    /**
     * Called by the Edit (pencil) button built into ea limit card
     *
     * @param limit the SpendingLimit to edit
     */
    public void handleEditLimit(SpendingLimit limit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Edit Spending Limit");
        dialog.setHeaderText("Update limit for " + limit.category());

        ComboBox<String> catCombo = new ComboBox<>();
        CategoryRegistry.getAll().forEach(c -> catCombo.getItems().add(c.displayName()));
        catCombo.setValue(limit.category());

        TextField amtField = new TextField(String.valueOf(limit.amount()));

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setVisible(false);

        VBox content = new VBox(8,
                new Label("Category:"), catCombo,
                new Label("Amount:"),   amtField,
                errorLabel);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStyles(dialog.getDialogPane());

        // block OK if edit to a cat that already has a diff limit
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String newCat = catCombo.getValue();
            double amt = -1;
            try { amt = Double.parseDouble(amtField.getText().trim()); } catch (NumberFormatException ignored) {}

            if (amt <= 0) {
                errorLabel.setText("Please enter a valid amount greater than 0.");
                errorLabel.setVisible(true);
                event.consume();
            } else if (!newCat.equalsIgnoreCase(limit.category())
                    && limitController.categoryLimitExists(newCat)) {
                errorLabel.setText("A limit for " + newCat +
                        " already exists, please select that limit to edit.");
                errorLabel.setVisible(true);
                event.consume();
            } else {
                errorLabel.setVisible(false);
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                double amt = Double.parseDouble(amtField.getText().trim());
                limitController.editLimit(limit.id(), catCombo.getValue(), amt);
                // refresh() fires automatically via onSessionUpdated()
            }
        });
    }

    /**
     * Called by the "x" button built into each limit card
     *
     * @param limit the SpendingLimit to delete
     */
    public void handleDeleteLimit(SpendingLimit limit) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        initDialog(confirm);
        confirm.setTitle("Delete Limit");
        confirm.setHeaderText("Delete spending limit for " + limit.category() + "?");
        confirm.setContentText("This action cannot be undone.");
        applyStyles(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                limitController.deleteLimit(limit.id());
                // refresh() fires automatically via onSessionUpdated()
            }
        });
    }

    // FOOTER : save
    @FXML
    private void handleSave() {
        navigationController.requestSave();
        syncSaveButton();
    }

    // REFRESH HELPERS

    // full refresh of all four islands; called after every session change
    private void refresh() {
        refreshPieChart();
        refreshLimits();
        refreshTodaysEntries();
        refreshLimitStatusLabel(txCategoryCombo.getValue());
        syncSaveButton();
        syncUndoButton();
    }

    // rebuild the pie chart from this week's spending totals
    private void refreshPieChart() {
        // capture and reset immediately; prevents limit triggered refresh
        // from inherenting stale true val due to nested runLater timing
        boolean shouldAnimate = animatePieOnNextRefresh;
        animatePieOnNextRefresh = false;

        List<SpendingCalculator.PieSlice> slices =
                transactionController.getPieChartData();

        if (slices.equals(lastPieSlices) && !shouldAnimate) return;
        lastPieSlices = slices;

        spendingPieChart.getData().clear();

        // Show placeholder if empty
        if (slices.isEmpty()) {
            spendingPieChart.setVisible(false);
            spendingPieChart.setManaged(false);
            noSpendingDataLabel.setVisible(true);
            noSpendingDataLabel.setManaged(true);
            return;
        }

        // Hide placeholder when data exists
        spendingPieChart.setVisible(true);
        spendingPieChart.setManaged(true);
        noSpendingDataLabel.setVisible(false);
        noSpendingDataLabel.setManaged(false);

        slices.forEach(slice -> {
            PieChart.Data data = new PieChart.Data(
                    slice.category() + " " +
                            String.format("%.0f%%", slice.percent()),
                    slice.amount());

            spendingPieChart.getData().add(data);
        });

        Platform.runLater(() -> {
            Popup piePopup = new Popup();
            Label pieLabel = new Label();
            pieLabel.getStyleClass().add("chart-hover-label");
            piePopup.getContent().add(pieLabel);

            int i = 0;
            for (PieChart.Data d : spendingPieChart.getData()) {
                if (i >= slices.size()) break;
                SpendingCalculator.PieSlice slice = slices.get(i);
                String color = CategoryRegistry.colorFor(slice.category());
                if (d.getNode() != null) {
                    d.getNode().setStyle("-fx-pie-color: " + color + ";");

                    // cursor follow hover
                    d.getNode().setOnMouseMoved(e -> {
                        pieLabel.setText(String.format("%s: $%.2f (%.0f%%)",
                                slice.category(), slice.amount(), slice.percent()));
                        piePopup.show(STRIVEApp.getPrimaryStage(),
                                e.getScreenX() + 14, e.getScreenY() + 14);
                    });
                    d.getNode().setOnMouseExited(e -> piePopup.hide());
                }
                i++;
            }

            for (javafx.scene.Node item :
                    spendingPieChart.lookupAll(".chart-legend-item")) {
                if (!(item instanceof javafx.scene.control.Label)) continue;
                javafx.scene.control.Label label = (javafx.scene.control.Label) item;
                String text = label.getText(); // e.g. "Housing 100%"
                javafx.scene.Node symbol = label.getGraphic();
                if (symbol == null) continue;
                slices.stream()
                        .filter(s -> text.startsWith(s.category()))
                        .findFirst()
                        .ifPresent(s -> symbol.setStyle(
                                "-fx-background-color: " + CategoryRegistry.colorFor(s.category()) + ";"));
            }

            if (shouldAnimate) {
                if (!pieAnimatedOnce) {
                    spendingPieChart.setScaleX(.15);
                    spendingPieChart.setScaleY(.15);

                    ScaleTransition grow = new ScaleTransition(Duration.millis(550), spendingPieChart);
                    grow.setToX(1);
                    grow.setToY(1);
                    grow.play();

                    pieAnimatedOnce = true;
                } else {
                    // subsequent transactions: subtle pulse to signal the update
                    ScaleTransition pulse = new ScaleTransition(Duration.millis(160), spendingPieChart);
                    pulse.setFromX(1.0);
                    pulse.setFromY(1);
                    pulse.setToX(1.06);
                    pulse.setToY(1.06);
                    pulse.setAutoReverse(true);
                    pulse.setCycleCount(2);
                    pulse.play();
                }
            }
        });
    }

    // rebuild the Spending Limits island; one card per active limit
    private void refreshLimits() {
        limitsContainer.getChildren().clear();

        List<LimitCalculator.LimitBarData> bars =
                transactionController.getLimitBarData();

        List<SpendingLimit> limits =
                limitController.getAllLimits();

        // Show placeholder if no limits yet
        if (limits.isEmpty()) {
            noLimitsDataLabel.setVisible(true);
            noLimitsDataLabel.setManaged(true);

            return;
        }

        // Hide placeholder once populated
        noLimitsDataLabel.setVisible(false);
        noLimitsDataLabel.setManaged(false);

        for (int i = 0; i < limits.size() && i < bars.size(); i++) {
            limitsContainer.getChildren().add(
                    buildLimitCard(limits.get(i), bars.get(i)));
        }
    }

    // repop today's entries table
    private void refreshTodaysEntries() {
        todaysEntriesTable.getItems().setAll(
                transactionController.getSessionTransactions());
    }

    /**
     * Updates the "Limit: $0.00 remaining" / "Over limit by $0.00" label
     * in the Enter Transaction from whenevery the cat dropdown changes
     */
    private void refreshLimitStatusLabel(String category) {
        if (category == null) {
            txLimitStatusLabel.setText("");
            return;
        }
        limitController.getLimitForCategory(category).ifPresentOrElse(limit -> {
            double spent = transactionController.getLimitBarData().stream()
                    .filter(b -> b.category().equalsIgnoreCase(category))
                    .mapToDouble(LimitCalculator.LimitBarData::spent)
                    .findFirst().orElse(0.0);
            double remaining = limit.amount() - spent;
            if (remaining == 0) {
                txLimitStatusLabel.setText("Limit reached");
                if(!txLimitStatusLabel.getStyleClass().contains("label-over-limit")) {
                    txLimitStatusLabel.getStyleClass().add("label-over-limit");
                }
            } else if (remaining > 0) {
                txLimitStatusLabel.setText(String.format("Limit: $%.2f remaining", remaining));
                txLimitStatusLabel.getStyleClass().removeAll("label-over-limit");
            } else {
                txLimitStatusLabel.setText(String.format("Over limit by $%.2f", Math.abs(remaining)));
                if (!txLimitStatusLabel.getStyleClass().contains("label-over-limit")) {
                    txLimitStatusLabel.getStyleClass().add("label-over-limit");
                }
            }
        }, () -> {
            txLimitStatusLabel.setText("No limit set");
            txLimitStatusLabel.getStyleClass().removeAll("label-over-limit");
        });
    }

    // LIMIT CARD BUILDER

    /**
     * Creates on limit card VBox for the limitsContainer
     *
     * @param limit the SPendingLimit from the backend
     * @param bar the calculated display data (spent, fillPercent, isOver)
     */
    private VBox buildLimitCard(SpendingLimit limit, LimitCalculator.LimitBarData bar) {
        String color = CategoryRegistry.colorFor(limit.category());
        boolean isOver = bar.isOver();

        // color dot + category name
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 16px;");

        Label catLabel = new Label(limit.category());
        catLabel.getStyleClass().add("limit-category-label");

        // amount remaining or over
        Label amtLabel = new Label(
                bar.spent() == bar.limitAmount()
                ? "Limit reached"
                : isOver
                ? String.format("-$%.2f over limit", bar.spent() - bar.limitAmount())
                : String.format("$%.2f remaining", bar.limitAmount() - bar.spent()));
        amtLabel.getStyleClass().add(isOver ? "label-over-limit" : "label-under-limit");

        HBox titleRow = new HBox(6, dot, catLabel);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // progress bar (clamped to 1.0 max)
        ProgressBar progressBar = new ProgressBar(bar.fillPercent() / 100.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add(isOver ? "limit-bar-over" : "limit-bar-ok");

        // spent / limit label below bar
        Label spentLabel = new Label(
                String.format("$%.2f / $%.2f", bar.spent(), bar.limitAmount()));
        spentLabel.getStyleClass().add("limit-spent-label");

        // edit button — passes the actual SpendingLimit object
        Button editBtn = new Button("✎");
        editBtn.getStyleClass().add("icon-btn");
        editBtn.setOnAction(e -> handleEditLimit(limit));

        // delete button — passes the actual SpendingLimit object
        Button deleteBtn = new Button("×");
        deleteBtn.getStyleClass().add("icon-btn-danger");
        deleteBtn.setOnAction(e -> handleDeleteLimit(limit));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(4, spacer, editBtn, deleteBtn);
        actionRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        HBox headerRow = new HBox();
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(titleRow, headerSpacer, actionRow);

        VBox card = new VBox(6, headerRow, progressBar, spentLabel, amtLabel);
        card.getStyleClass().add("limit-card");
        return card;
    }
}
