package com.cobra;

import com.cobra.types.*;

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
		DBModel dbmodel = DBModel.getInstance();
		// DBModel dbmodel = new DBModel();
		dbmodel.initDB();
		ArrayList<Transaction> transactionList = dbmodel.fetchTransactions();
		ArrayList<Limit> limitList = dbmodel.fetchLimits();
		System.out.println("Transaction List " + transactionList);
		System.out.println("Limit List " + limitList);
		launch();
	}
}
