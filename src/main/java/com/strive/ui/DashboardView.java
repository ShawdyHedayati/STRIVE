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
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * JavaFX controller for the Dashboard view ({@code dashboard.fxml}).
 *
 * Terminology note: JavaFX calls FXML-backed classes "controllers",
 * but in CoBRA's layered architecture this class is the view — it
 * owns only UI state and rendering logic. All business decisions and data
 * mutations go through {@link TransactionController} and {@link LimitController}.
 *
 * The Dashboard is divided into four visual islands:
 *
 *   Spending Pie Chart — this week's spending broken down by category
 *   Spending Limits — one dynamically built limit card per active limit
 *   Enter Transaction — inline form for adding a new transaction
 *   Today's Entries — table of transactions added in this session
 *
 * Refresh lifecycle: This view implements {@link SessionListener} and
 * registers itself via {@link TransactionController#addListener} during
 * calls {@link #onSessionUpdated()}, which schedules {@link #refresh()} on the
 * JavaFX Application Thread via {@code Platform.runLater}.
 *
 * Controller wiring: Backend controllers are retrieved from
 * {@link AppContext} in {@link #initialize} rather than being injected directly,
 * because JavaFX instantiates this class via reflection when loading the FXML.
 *
 * @see BaseView
 * @see ChartsView
 * @see AppContext
 */

public class DashboardView extends BaseView {
    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 1: Spending Pie Chart
    // -------------------------------------------------------------------------

    /** The pie chart displaying this week's spending breakdown by category. */
    @FXML private PieChart spendingPieChart;
    /**
     * {@code true} after the pie chart has played its initial grow animation.
     * Prevents the full grow animation from replaying on subsequent refreshes —
     * later updates use a shorter pulse animation instead.
     */
    private boolean pieAnimatedOnce = false;
    /**
     * Set to {@code true} by transaction mutation handlers before calling the
     * controller, so the next {@link #refreshPieChart()} call plays an animation.
     * Reset to {@code false} immediately at the start of {@link #refreshPieChart()}
     * to avoid stale values leaking across nested {@code Platform.runLater} calls.
     */
    private boolean animatePieOnNextRefresh = false;

    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 2: Spending Limits
    // -------------------------------------------------------------------------

    /**
     * Dynamic container for limit cards; children are fully rebuilt by
     * {@link #refreshLimits()} on every session update.
     */
    @FXML private VBox limitsContainer;
    /** Placeholder label shown when no spending data exists for the week. */
    @FXML private Label noSpendingDataLabel;
    /** Placeholder label shown when no spending limits have been set. */
    @FXML private Label noLimitsDataLabel;

    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 3: Enter Transaction
    // -------------------------------------------------------------------------

    /** Dropdown for selecting the spending category of a new transaction. */
    @FXML private ComboBox<String> txCategoryCombo;
    /** Text field for entering the dollar amount of a new transaction. */
    @FXML private TextField txAmountField;
    /**
     * Status label below the category dropdown showing the remaining budget
     * for the selected category (e.g. {@code "Limit: $42.50 remaining"},
     * {@code "Over limit by $10.00"}, or {@code "No limit set"}).
     */
    @FXML private Label txLimitStatusLabel;
    /** Date picker for selecting the date of a new transaction; defaults to today. */
    @FXML private DatePicker txDatePicker;
    /** Undo button; enabled only when there is at least one undoable command. */
    @FXML private Button undoBtn;

    // -------------------------------------------------------------------------
    // FXML-injected fields — Island 4: Today's Entries
    // -------------------------------------------------------------------------

    /** Table displaying transactions added during the current session. */
    @FXML private TableView<Transaction> todaysEntriesTable;
    /** Table column displaying each transaction's category. */
    @FXML private TableColumn<Transaction, String> todaysCategoryCol;
    /** Table column displaying each transaction's formatted dollar amount. */
    @FXML private TableColumn<Transaction, String> todaysAmountCol;
    /** Table column displaying each transaction's date. */
    @FXML private TableColumn<Transaction, String> todaysDateCol;
    /** Edit button; enabled only when a row is selected in {@link #todaysEntriesTable}. */
    @FXML private Button editTxBtn;
    /** Delete button; enabled only when a row is selected in {@link #todaysEntriesTable}. */
    @FXML private Button deleteTxBtn;

    // -------------------------------------------------------------------------
    // Backend controllers and view state
    // -------------------------------------------------------------------------

    /** Handles all transaction mutations and supplies display-ready data to this view. */
    private TransactionController transactionController;

    /** Handles all limit mutations and supplies the active limit list. */
    private LimitController limitController;

    /**
     * Caches the most recently rendered pie slices so {@link #refreshPieChart()}
     * can skip a full rebuild when the data hasn't changed. Compared via
     * {@link List#equals} before redrawing.
     */
    private List<SpendingCalculator.PieSlice> lastPieSlices = List.of();

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Called by JavaFX after all {@code @FXML} fields have been injected.
     * Wires backend controllers from {@link AppContext}, configures the table
     * and form controls, registers this view as a {@link SessionListener},
     * and performs the initial data load.
     *
     * @param url unused — required by {@link javafx.fxml.Initializable}
     * @param rb  unused — required by {@link javafx.fxml.Initializable}
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Fetch backend controllers from the static registry populated at startup
        transactionController = AppContext.getTransactionController();
        limitController = AppContext.getLimitController();
        navigationController = AppContext.getNavigationController();

        // CONSTRAINED_FLEX_LAST_COLUMN makes the last column absorb leftover width,
        // preventing a horizontal scrollbar from appearing. Required due to a JavaFX
        // layout quirk where the default policy leaves a gap.
        todaysEntriesTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Register as a session listener so onSessionUpdated() → refresh() fires
        // automatically after every apply/undo/flush without polling
        transactionController.addListener(this);

        // Populate the category dropdown from CategoryRegistry — single source of truth
        CategoryRegistry.getAll().forEach(c ->
                txCategoryCombo.getItems().add(c.displayName()));
        txCategoryCombo.getSelectionModel().selectFirst();

        // Default the date picker to today so the user rarely needs to change it
        txDatePicker.setValue(LocalDate.now());

        // Update the limit status label whenever the category selection changes
        txCategoryCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> refreshLimitStatusLabel(newVal));

        // Wire table columns with lambda cell factories.
        // Java records don't expose JavaBean-style getters, so PropertyValueFactory
        // won't work — lambdas calling the record accessor methods are required.
        todaysCategoryCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().category()));
        todaysAmountCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", data.getValue().amount())));
        todaysDateCol.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(
                        data.getValue().date().toString()));

        // Edit and delete buttons start disabled; enabled only when a row is selected
        editTxBtn.setDisable(true);
        deleteTxBtn.setDisable(true);
        todaysEntriesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> {
                    boolean hasSel = newSel != null;
                    editTxBtn.setDisable(!hasSel);
                    deleteTxBtn.setDisable(!hasSel);
                });

        syncSaveButton(); // set initial save button visual state

        refresh(); // populate all four islands with data on first render
    }

    // -------------------------------------------------------------------------
    // SessionListener
    // -------------------------------------------------------------------------

    /**
     * Called by {@link com.strive.session.SessionManager} after any command is
     * applied, undone, flushed, or discarded.
     *
     * Schedules a full dashboard refresh on the JavaFX Application Thread via
     * {@code Platform.runLater}, since {@code SessionManager} may call this from
     * any thread.
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
     * Handles the Charts navigation button click. Notifies the navigation
     * controller and switches the active FXML view. Shows an error dialog
     * if the Charts FXML cannot be loaded.
     */
    @FXML
    private void navigateToCharts() {
        navigationController.goToCharts();
        try {
            STRIVEApp.navigateTo("charts");
        } catch (IOException e) {
            showError("Navigation Error", "Could not open Charts view: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Island 3 handlers — Enter Transaction
    // -------------------------------------------------------------------------

    /**
     * Handles the Add Transaction button click. Validates the form inputs,
     * then delegates to {@link TransactionController#addTransaction}.
     *
     * Validation rules:
     *   Category must be selected (non-null).
     *   Amount must be a positive number.
     *
     * On success, the form fields are reset and {@link #refresh()} fires
     * automatically via {@link #onSessionUpdated()}.
     */
    @FXML
    private void handleAddTransaction() {
        String category = txCategoryCombo.getValue();
        String amountText = txAmountField.getText().trim();
        LocalDate date = txDatePicker.getValue();

        // Validate required fields before touching the controller
        if (category == null || amountText.isEmpty()) {
            showError("Validation Error", "Please select a category and enter an amount.");

            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(amountText);
            if (amount <= 0) throw new NumberFormatException(); // treat non-positive as invalid
        } catch (NumberFormatException e) {
            showError("Invalid Amount", "Please enter a positive number (e.g. 12.50).");

            return;
        }

        // Flag the pie chart to animate on the next refresh before calling the controller,
        // so the animation plays in response to this specific user action
        animatePieOnNextRefresh = true;
        transactionController.addTransaction(amount, category, date);

        // Reset the form for the next entry; date stays as today
        txAmountField.clear();
        txDatePicker.setValue(LocalDate.now());
        // refresh() fires automatically via onSessionUpdated() — no explicit call needed
    }

    /**
     * Keeps the Undo button's enabled/disabled state in sync with whether
     * there are undoable commands on the session log.
     */
    private void syncUndoButton() {
        if (undoBtn == null) return;
        undoBtn.setDisable(
                !AppContext.getTransactionController().canUndo());
    }

    /**
     * Handles the Undo button click. Flags the pie chart to animate, then
     * delegates to {@link TransactionController#undo()}.
     */
    @FXML
    private void handleUndo() {

        animatePieOnNextRefresh = true;

        transactionController.undo();
    }

    // -------------------------------------------------------------------------
    // Island 4 handlers — Today's Entries
    // -------------------------------------------------------------------------

    /**
     * Handles the Edit Transaction button click. Opens a modal dialog
     * pre-populated with the selected transaction's current values.
     * Validates the new amount inline (via an OK button event filter) before
     * allowing the dialog to close.
     *
     * On confirmation, delegates to {@link TransactionController#editTransaction}.
     * Refresh fires automatically via {@link #onSessionUpdated()}.
     */
    @FXML
    private void handleEditTransaction() {
        Transaction selected = todaysEntriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return; // guard: button should already be disabled when no selection

        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Edit Transaction");
        dialog.setHeaderText("Update transaction details");

        // Pre-populate fields with the selected transaction's current values
        ComboBox<String> catCombo = new ComboBox<>();
        CategoryRegistry.getAll().forEach(c -> catCombo.getItems().add(c.displayName()));
        catCombo.setValue(selected.category());

        TextField amtField = new TextField(String.valueOf(selected.amount()));

        DatePicker dp = new DatePicker(selected.date());

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

        // Event filter on the OK button: validate amount before allowing the dialog to close
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                double amt = Double.parseDouble(amtField.getText().trim());
                if (amt <= 0) throw new NumberFormatException();
                errorLabel.setVisible(false); // clear any previous error
            } catch (NumberFormatException e) {
                errorLabel.setText("Please enter a valid amount greater than 0.");
                errorLabel.setVisible(true);
                event.consume(); // prevent the dialog from closing on invalid input
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

    /**
     * Handles the Delete Transaction button click. Shows a confirmation dialog
     * before delegating to {@link TransactionController#deleteTransaction}.
     *
     * Refresh fires automatically via {@link #onSessionUpdated()} if confirmed.
     */
    @FXML
    private void handleDeleteTransaction() {
        Transaction selected = todaysEntriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return; // guard: button should already be disabled when no selection

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

    // -------------------------------------------------------------------------
    // Island 2 handlers — Spending Limits
    // -------------------------------------------------------------------------

    /**
     * Handles the Add Limit button click. Opens a modal dialog for entering a
     * new category and weekly cap amount.
     *
     * The OK button event filter enforces two rules inline:
     *
     *   Amount must be a positive number.
     *   A limit for the selected category must not already exist
     *   (one-per-category rule).
     *
     * On confirmation, delegates to {@link LimitController#addLimit}.
     */
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

        // Prevent the dialog from closing on OK if validation fails
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String cat = catCombo.getValue();
            String amtText = amtField.getText().trim();
            double amt = -1;
            try { amt = Double.parseDouble(amtText); } catch (NumberFormatException ignored) {}

            if (amt <= 0) {
                // Invalid amount — show error and block close
                errorLabel.setText("Please enter a valid amount greater than 0.");
                errorLabel.setVisible(true);
                event.consume();
            } else if (limitController.categoryLimitExists(cat)) {
                // Duplicate category — show error and block close
                errorLabel.setText("A limit for " + cat +
                        " already exists, please select that limit to edit.");
                errorLabel.setVisible(true);
                event.consume();
            } else {
                errorLabel.setVisible(false); // clear any previous error — validation passed
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
     * Opens an edit dialog for an existing spending limit. Called by the pencil
     * (✎) button built dynamically into each limit card by {@link #buildLimitCard}.
     *
     * The OK button event filter enforces:
     *
     *   Amount must be positive.
     *   If the category is being changed, the new category must not already
     *   have a limit (one-per-category rule; editing in-place is always allowed).
     *
     * On confirmation, delegates to {@link LimitController#editLimit}.
     *
     * @param limit the {@link SpendingLimit} to edit; pre-populates the dialog fields
     */
    public void handleEditLimit(SpendingLimit limit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        initDialog(dialog);
        dialog.setTitle("Edit Spending Limit");
        dialog.setHeaderText("Update limit for " + limit.category());

        // Pre-populate fields with the current limit's values
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

        // Block OK if the new category already has a different limit
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
                // Category changed to one that already has a limit — block
                errorLabel.setText("A limit for " + newCat +
                        " already exists, please select that limit to edit.");
                errorLabel.setVisible(true);
                event.consume();
            } else {
                errorLabel.setVisible(false); // validation passed
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
     * Shows a confirmation dialog and deletes the given spending limit if
     * confirmed. Called by the × button built into each limit card by
     * {@link #buildLimitCard}.
     *
     * Delegates to {@link LimitController#deleteLimit}. Refresh fires
     * automatically via {@link #onSessionUpdated()} if confirmed.
     *
     * @param limit the {@link SpendingLimit} to delete
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
     * Performs a full refresh of all four dashboard islands. Called after every
     * session state change via {@link #onSessionUpdated()}.
     *
     * Delegates to four targeted sub-refresh methods so each island can be
     * reasoned about independently.
     */
    private void refresh() {
        refreshPieChart();
        refreshLimits();
        refreshTodaysEntries();
        refreshLimitStatusLabel(txCategoryCombo.getValue());
        syncSaveButton();
        syncUndoButton();
    }

    /**
     * Rebuilds the spending pie chart from this week's spending totals.
     *
     * Uses {@link #lastPieSlices} to skip a full rebuild when the data
     * hasn't changed and no animation was requested — this avoids unnecessary
     * chart flicker on refreshes triggered by non-transaction events (e.g.
     * limit changes).
     *
     * Animation behaviour:
     *
     *   First transaction added → full grow animation (scale from 15% to 100%)
     *   Subsequent transactions → short pulse animation (scale to 106% and back)
     *   No animation flag → chart redraws silently
     *
     * The {@code animatePieOnNextRefresh} flag is captured and reset at the
     * very start to prevent stale {@code true} values from leaking across nested
     * {@code Platform.runLater} timing boundaries.
     */
    private void refreshPieChart() {
        // Capture and reset the animation flag immediately to avoid stale values
        // from leaking across nested Platform.runLater timing boundaries
        boolean shouldAnimate = animatePieOnNextRefresh;
        animatePieOnNextRefresh = false;

        List<SpendingCalculator.PieSlice> slices =
                transactionController.getPieChartData();

        // Skip rebuild if data is unchanged and no animation was requested
        if (slices.equals(lastPieSlices) && !shouldAnimate) return;
        lastPieSlices = slices;

        spendingPieChart.getData().clear();

        if (slices.isEmpty()) {
            // No spending data this week — show placeholder, hide chart
            spendingPieChart.setVisible(false);
            spendingPieChart.setManaged(false);
            noSpendingDataLabel.setVisible(true);
            noSpendingDataLabel.setManaged(true);
            return;
        }

        // Data exists — hide placeholder, show chart
        spendingPieChart.setVisible(true);
        spendingPieChart.setManaged(true);
        noSpendingDataLabel.setVisible(false);
        noSpendingDataLabel.setManaged(false);

        // Add one PieChart.Data entry per slice (label = "Category XX%", value = amount)
        slices.forEach(slice -> {
            PieChart.Data data = new PieChart.Data(
                    slice.category() + " " +
                            String.format("%.0f%%", slice.percent()),
                    slice.amount());

            spendingPieChart.getData().add(data);
        });

        // Defer node styling and hover wiring until JavaFX has finished rendering the chart,
        // since chart nodes are not available in the scene graph until after layout
        Platform.runLater(() -> {
            // Set up the hover tooltip popup
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
                    // Apply the category's canonical colour to the slice node
                    d.getNode().setStyle("-fx-pie-color: " + color + ";");

                    // Show a cursor-following tooltip with category, amount, and percent
                    d.getNode().setOnMouseMoved(e -> {
                        pieLabel.setText(String.format("%s: $%.2f (%.0f%%)",
                                slice.category(), slice.amount(), slice.percent()));
                        piePopup.show(STRIVEApp.getPrimaryStage(),
                                e.getScreenX() + 14, e.getScreenY() + 14); // offset avoids cursor overlap
                    });
                    d.getNode().setOnMouseExited(e -> piePopup.hide());
                }
                i++;
            }

            // Colour legend symbols to match their slice colours
            for (javafx.scene.Node item :
                    spendingPieChart.lookupAll(".chart-legend-item")) {
                if (!(item instanceof javafx.scene.control.Label)) continue;
                javafx.scene.control.Label label = (javafx.scene.control.Label) item;
                String text = label.getText(); // e.g. "Housing 100%
                javafx.scene.Node symbol = label.getGraphic();
                if (symbol == null) continue;

                // Match the legend item to its slice by category name prefix
                slices.stream()
                        .filter(s -> text.startsWith(s.category()))
                        .findFirst()
                        .ifPresent(s -> symbol.setStyle(
                                "-fx-background-color: " + CategoryRegistry.colorFor(s.category()) + ";"));
            }

            // Play the appropriate animation based on whether this is the first render
            if (shouldAnimate) {
                if (!pieAnimatedOnce) {
                    // First-ever render: grow from 15% to full size
                    spendingPieChart.setScaleX(.15);
                    spendingPieChart.setScaleY(.15);

                    ScaleTransition grow = new ScaleTransition(Duration.millis(550), spendingPieChart);
                    grow.setToX(1);
                    grow.setToY(1);
                    grow.play();

                    pieAnimatedOnce = true;
                } else {
                    // Subsequent updates: brief pulse to 106% to signal the change
                    ScaleTransition pulse = new ScaleTransition(Duration.millis(160), spendingPieChart);
                    pulse.setFromX(1.0);
                    pulse.setFromY(1);
                    pulse.setToX(1.06);
                    pulse.setToY(1.06);
                    pulse.setAutoReverse(true); // bounce back to 100%
                    pulse.setCycleCount(2); // out and back = 2 cycles
                    pulse.play();
                }
            }
        });
    }

    /**
     * Rebuilds the Spending Limits island by clearing {@link #limitsContainer}
     * and adding one {@link #buildLimitCard} per active limit.
     *
     * Shows {@link #noLimitsDataLabel} as a placeholder if no limits are set;
     * hides it once at least one limit exists.
     */
    private void refreshLimits() {
        limitsContainer.getChildren().clear(); // remove all existing limit cards

        List<LimitCalculator.LimitBarData> bars =
                transactionController.getLimitBarData();

        List<SpendingLimit> limits =
                limitController.getAllLimits();

        if (limits.isEmpty()) {
            // No limits set — show placeholder
            noLimitsDataLabel.setVisible(true);
            noLimitsDataLabel.setManaged(true);

            return;
        }

        // Limits exist — hide placeholder and build one card per limit
        noLimitsDataLabel.setVisible(false);
        noLimitsDataLabel.setManaged(false);

        // limits and bars are parallel lists in the same order — zip by index
        for (int i = 0; i < limits.size() && i < bars.size(); i++) {
            limitsContainer.getChildren().add(
                    buildLimitCard(limits.get(i), bars.get(i)));
        }
    }

    /**
     * Repopulates the Today's Entries table with transactions added during
     * the current session (i.e. since startup).
     */
    private void refreshTodaysEntries() {
        // getSessionTransactions() filters out pre-loaded IDs captured at startup
        todaysEntriesTable.getItems().setAll(
                transactionController.getSessionTransactions());
    }

    /**
     * Updates the limit status label below the category dropdown to reflect the
     * remaining budget (or over-limit amount) for the currently selected category.
     *
     * Three states:
     *   Exactly at limit: {@code "Limit reached"} (red)
     *   Under limit:      {@code "Limit: $X.XX remaining"} (normal)
     *   Over limit:       {@code "Over limit by $X.XX"} (red)
     *   No limit set:     {@code "No limit set"} (normal)
     *
     * @param category the currently selected category display name;
     *                 clears the label if {@code null}
     */
    private void refreshLimitStatusLabel(String category) {
        if (category == null) {
            txLimitStatusLabel.setText("");
            return;
        }

        limitController.getLimitForCategory(category).ifPresentOrElse(limit -> {
            // Find the current spend for this category from the limit bar data
            double spent = transactionController.getLimitBarData().stream()
                    .filter(b -> b.category().equalsIgnoreCase(category))
                    .mapToDouble(LimitCalculator.LimitBarData::spent)
                    .findFirst().orElse(0.0); // no bar data yet → treat as zero spent

            double remaining = limit.amount() - spent;

            if (remaining == 0) {
                // Exactly at the limit
                txLimitStatusLabel.setText("Limit reached");
                if(!txLimitStatusLabel.getStyleClass().contains("label-over-limit")) {
                    txLimitStatusLabel.getStyleClass().add("label-over-limit");
                }
            } else if (remaining > 0) {
                // Under the limit
                txLimitStatusLabel.setText(String.format("Limit: $%.2f remaining", remaining));
                txLimitStatusLabel.getStyleClass().removeAll("label-over-limit");
            } else {
                // Over the limit — remaining is negative, so Math.abs gives the overage
                txLimitStatusLabel.setText(String.format("Over limit by $%.2f", Math.abs(remaining)));
                if (!txLimitStatusLabel.getStyleClass().contains("label-over-limit")) {
                    txLimitStatusLabel.getStyleClass().add("label-over-limit");
                }
            }
        }, () -> {
            // No limit set for this category
            txLimitStatusLabel.setText("No limit set");
            txLimitStatusLabel.getStyleClass().removeAll("label-over-limit");
        });
    }

    // -------------------------------------------------------------------------
    // Limit card builder
    // -------------------------------------------------------------------------

    /**
     * Builds and returns a {@link VBox} "card" widget representing a single
     * spending limit in the Spending Limits island.
     *
     * Card layout (top to bottom):
     *   Header row: colored dot + category name + spacer + edit (✎) and delete (×) buttons
     *   Progress bar: filled to {@link LimitCalculator.LimitBarData#fillPercent()} / 100,
     *   colored green ({@code limit-bar-ok}) or red ({@code limit-bar-over})
     *   Spent / limit label: e.g. {@code "$87.50 / $150.00"}
     *   Amount label: remaining budget or over-limit amount
     *
     * @param limit the {@link SpendingLimit} from the backend (provides category and limit amount)
     * @param bar   the pre-calculated display data from the BLL (provides spent, fillPercent, isOver)
     * @return the fully configured card {@link VBox}, ready to add to {@link #limitsContainer}
     */
    private VBox buildLimitCard(SpendingLimit limit, LimitCalculator.LimitBarData bar) {
        String color = CategoryRegistry.colorFor(limit.category());
        boolean isOver = bar.isOver();

        // Coloured dot indicator matching the category's chart colour
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 16px;");

        Label catLabel = new Label(limit.category());
        catLabel.getStyleClass().add("limit-category-label");

        // Amount label: three states — exactly at limit, over limit, or under limit
        Label amtLabel = new Label(
                bar.spent() == bar.limitAmount()
                ? "Limit reached"
                : isOver
                ? String.format("-$%.2f over limit", bar.spent() - bar.limitAmount())
                : String.format("$%.2f remaining", bar.limitAmount() - bar.spent()));
        amtLabel.getStyleClass().add(isOver ? "label-over-limit" : "label-under-limit");

        HBox titleRow = new HBox(6, dot, catLabel);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Progress bar: fillPercent is already clamped to [0, 100] by LimitCalculator,
        // so dividing by 100.0 always produces a valid [0.0, 1.0] ProgressBar value
        ProgressBar progressBar = new ProgressBar(bar.fillPercent() / 100.0);
        progressBar.setMaxWidth(Double.MAX_VALUE); // stretch to fill the card width
        progressBar.getStyleClass().add(isOver ? "limit-bar-over" : "limit-bar-ok");

        // Spent / limit summary below the progress bar
        Label spentLabel = new Label(
                String.format("$%.2f / $%.2f", bar.spent(), bar.limitAmount()));
        spentLabel.getStyleClass().add("limit-spent-label");

        // Edit button — passes the full SpendingLimit so the dialog can pre-populate
        Button editBtn = new Button("✎"); // turns out emojis work
        editBtn.getStyleClass().add("icon-btn");
        editBtn.setOnAction(e -> handleEditLimit(limit));

        // Delete button — passes the full SpendingLimit so the controller can find it by id
        Button deleteBtn = new Button("×");
        deleteBtn.getStyleClass().add("icon-btn-danger");
        deleteBtn.setOnAction(e -> handleDeleteLimit(limit));

        // Spacer pushes edit/delete buttons to the right side of the header row
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(4, spacer, editBtn, deleteBtn);
        actionRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        // Combine title row and action row into the card header
        HBox headerRow = new HBox();
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(titleRow, headerSpacer, actionRow);

        // Assemble the card VBox with all components in order
        VBox card = new VBox(6, headerRow, progressBar, spentLabel, amtLabel);
        card.getStyleClass().add("limit-card");
        return card;
    }
}
