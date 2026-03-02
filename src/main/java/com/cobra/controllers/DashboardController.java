package com.cobra.controllers;

import com.cobra.models.Goal;
import com.cobra.models.Transaction;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

// For right now this is for taking table data and displaying it
// this will change because that isnt what we want for the dash
// but this will give better idea of general functionality
// Ian will build the database so the placeholder data is temporary as of right now

public class DashboardController {

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

    // intit() auto called by fjx when loaded
    // loads data into the tables

    @FXML

    public void initialize() {
        // transaction cols
        // which field from transaction will be displayed
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

        // DATABASE IS NOT SET UP
        // THIS WILL BE THE PLACEHOLDER FOR NOW I JUST NEED DATA TO SEE IF THIS WORKS
        ObservableList<Transaction> transactions = FXCollections.observableArrayList(
                new Transaction(1, 24.99, "Food", 20260301),
                new Transaction(2, 9.99, "Fun", 20260301),
                new Transaction(3, 50.00, "Utilities", 20260228)
        );

        TransactionTable.setItems(transactions);

        ObservableList<Goal> goals = FXCollections.observableArrayList(
                new Goal(1, 150.00, "Living", 20260401),
                new Goal(2, 100.00, "Food", 20260401),
                new Goal(3, 50.00, "Fun", 20260401)
        );

        GoalTable.setItems(goals);

        System.out.println("Dashboard Loaded!!!!!!!!!!!");
    }
}
