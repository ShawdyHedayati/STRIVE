package com.cobra;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

	@Override
	public void start(Stage stage) throws Exception {
		System.out.println("[MainApp] Starting STRIVE...");

		FXMLLoader fxml = new FXMLLoader(getClass().getResource("/views/Dashboard.fxml"));
		Scene scene = new Scene(fxml.load(), 900, 600);
		DashboardView view = fxml.getController();

		Controller controller = new Controller();
		view.setController(controller);

		stage.setTitle("STRIVE - Budget Tracker");
		stage.setScene(scene);
		stage.show();
		System.out.println("[MainApp] Ready");
	}

	public static void main(String[] args) {
		launch();
	}
}
