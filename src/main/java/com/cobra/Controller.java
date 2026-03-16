package com.cobra;

import com.cobra.cache.CacheManager;
import com.cobra.types.Goal;
import com.cobra.types.Transaction;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;

/*
for main dashboard
data read/write fo through cache manager

to add new feature:
add cache op to cache manage
add @FXML method that calls
call refreshUI at end for table update

TODO:
- delete
- add/edit/delete goal
- charts
*/

public class Controller {

	@FXML private TableView<Transaction> TransactionTable;
	@FXML private TableColumn<Transaction, Integer> TransactionIDCol;
	@FXML private TableColumn<Transaction, String> TransactionCategoryCol;
	@FXML private TableColumn<Transaction, Double> TransactionAmountCol;
	@FXML private TableColumn<Transaction, String> TransactionDateCol;

	@FXML private TableView<Goal> GoalTable;
	@FXML private TableColumn<Goal, Integer> GoalIDCol;
	@FXML private TableColumn<Goal, String> GoalCategoryCol;
	@FXML private TableColumn<Goal, Double> GoalAmountCol;
	@FXML private TableColumn<Goal, String> GoalDateCol;

	// reference to cache that i have made with my fingies
	private final CacheManager cache = CacheManager.getInstance();

	@FXML
	public void initialize() {
		// transaction cols
		TransactionIDCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().id()).asObject());
		TransactionCategoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category()));
		TransactionAmountCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().amount()).asObject());

		// for right now this is how i am choosing to handle date
		// yyyy-mm-dd
		// this will also apply to goals dates for right now
		TransactionDateCol.setCellValueFactory(data -> {
			int raw = data.getValue().date();
			String formatted = raw/10000 + "-" + String.format("%02d", (raw/100)%100) + "-" + String.format("%02d", raw%100);
			return new SimpleStringProperty(formatted);
		});

		// goal cols
		GoalIDCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().id()).asObject());
		GoalCategoryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category()));
		GoalAmountCol.setCellValueFactory(data -> new SimpleDoubleProperty(data.getValue().amount()).asObject());
		GoalDateCol.setCellValueFactory(data -> {
			int raw = data.getValue().date();
			String formatted = raw/10000 + "-" + String.format("%02d", (raw/100)%100) + "-" + String.format("%02d", raw%100);
			return new SimpleStringProperty(formatted);
		});

		refreshUI();

		System.out.println("Dashboard Loaded!!!!!!!!!!!");
	}

	@FXML
	private void onAddTransaction() {

		// we need info to enter
		String category = askInput("Add Transaction", "Category (e.g. Food):");
		String amountStr = askInput("Add Transaction", "Amount (e.g. 24.99):");
		String dateStr = askInput("Add Transaction", "Date (YYYYMMDD e.g. 20260301):");

		// for cancelling, do nothing if canceled
		if (category == null || amountStr == null || dateStr == null)
			return;

		try {
			// convert into usable variables
			double amount = Double.parseDouble(amountStr);
			int date = Integer.parseInt(dateStr);

			// add to cache and update table
			cache.addTransaction(category, amount, date);
			refreshUI();
		} catch (NumberFormatException e) {
			// incorrect input entered
			showAlert("Invalid input", "Amount must be a number, date must be YYYYMMDD.");
		}
	}

	@FXML
	private void onEditTransaction() {

		// user need to click on a row to edit
		Transaction selected = TransactionTable.getSelectionModel().getSelectedItem();

		// cancel thing again
		if (selected == null) {
			showAlert("No selection", "Please select a transaction to edit.");

			return;
		}

		// get our inputs, yet again
		String category = askInput("Edit Transaction", "Category:");
		String amountStr = askInput("Edit Transaction", "Amount:");
		String dateStr = askInput("Edit Transaction", "Date (YYYYMMDD):");

		// CANCELING AKJHDSLKAJHLGIUWYHDI
		if (category == null || amountStr == null || dateStr == null)
			return;

		try {
			// convert yet again
			double amount = Double.parseDouble(amountStr);
			int date = Integer.parseInt(dateStr);

			// cache update yippee
			cache.updateTransaction(selected.id(), category, amount, date);
			refreshUI();
		} catch (NumberFormatException e) {
			// invalid again
			showAlert("Invalid input", "Amount must be a number, date must be YYYYMMDD.");
		}
	}

	private void refreshUI() {
		TransactionTable.setItems(FXCollections.observableArrayList(cache.getTransactions()));
		GoalTable.setItems(FXCollections.observableArrayList(cache.getGoals()));
	}

	private String askInput(String title, String prompt) {
		TextInputDialog dialog = new TextInputDialog();

		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(prompt);

		return dialog.showAndWait().orElse(null);
	}

	private void showAlert(String title, String message){
		Alert alert = new Alert(Alert.AlertType.WARNING);

		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}
}