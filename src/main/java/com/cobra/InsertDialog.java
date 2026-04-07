package com.cobra;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class InsertDialog extends Dialog<InsertDialog.InsertResult> {

    public record InsertResult(double amount, String category) {}

    public InsertDialog() {
        setTitle("Add Transaction");
        setHeaderText("Enter transaction details");

        TextField amountField = new TextField();
        TextField categoryField = new TextField();
        amountField.setPromptText("Amount (e.g. 42.50)");
        categoryField.setPromptText("Category (e.g. Food)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Amount:"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryField, 1, 1);
        getDialogPane().setContent(grid);

        ButtonType insertBtn = new ButtonType("Insert", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(insertBtn, ButtonType.CANCEL);

        setResultConverter(btn -> {
            if (btn == insertBtn) {
                try {
                    double amount = Double.parseDouble(amountField.getText().trim());
                    String cat    = categoryField.getText().trim();
                    return new InsertResult(amount, cat);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
    }
}