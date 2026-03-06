package com.cobra;

import com.cobra.models.*;

import java.util.ArrayList;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

	@Override
	public void start(Stage stage) throws Exception {
		FXMLLoader fxml = new FXMLLoader(getClass().getResource("/views/dashboard.fxml"));
		Scene scene = new Scene(fxml.load(), 900, 600);
		stage.setTitle("Budget Tracker");
		stage.setScene(scene);
		stage.show();
	}

	public static void main(String[] args) {
		DBLayer dblayer = new DBLayer();
		dblayer.initDB();
		ArrayList<Transaction> transactionList = dblayer.fetchTransactions();
		ArrayList<Goal> goalList = dblayer.fetchGoals();
		System.out.println("Transaction List " + transactionList);
		System.out.println("Goal List " + goalList);
		launch();
	}
}
