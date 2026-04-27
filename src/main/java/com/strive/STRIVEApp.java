package com.strive;

import com.strive.bll.CSVExporter;
import com.strive.bll.ChartCalculator;
import com.strive.bll.LimitCalculator;
import com.strive.bll.SpendingCalculator;
import com.strive.controller.LimitController;
import com.strive.controller.NavigationController;
import com.strive.controller.TransactionController;
import com.strive.model.LimitDAO;
import com.strive.model.TransactionDAO;
import com.strive.session.SessionManager;

import javafx.application.Application;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * STRIVE Budget Application
 *
 * Wiring order: model -> session -> controller -> app context -> UI
 *
 * Navigation between dash and charts is handled by calling STRIVEApp.navigateTo("dashboard") or navigateTo("charts")
 * Each FXML controller retieves its backend controllers from AppContext
 */

	// TODO: IN MAC FULLSCREEN - dialog boxes (exit app no save, add transaction, edit transaction) is detected as a separate window and not a pop up
	// TODO: adding images to project (Logo, Icons)

public class STRIVEApp extends Application {
	private static Stage primaryStage;

	@Override
	public void start(Stage stage) throws Exception {
		primaryStage = stage;

		// MODEL LAYER - build database url and init DAOS
		// TODO: update path res for packaged distro
		File topDir = new File(System.getProperty("user.dir"));
		File dbFile = new File(topDir,
				"/src/main/resources/strive_test.db");
		String dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
		System.out.println("[STRIVEApp] DB: " + dbFile.getAbsolutePath());

		TransactionDAO transactionDAO = new TransactionDAO(dbUrl);
		LimitDAO limitDAO = new LimitDAO(dbUrl);

		// SESSION LAYER - load initial state from db
		SessionManager session = new SessionManager(transactionDAO, limitDAO);

		// CONTROLLER LAYER - ea controller shares same sesh
		SpendingCalculator spendingCalculator = new SpendingCalculator();
		LimitCalculator limitCalculator = new LimitCalculator();
		ChartCalculator chartCalculator= new ChartCalculator();
		CSVExporter csvExporter = new CSVExporter();

		TransactionController tc = new TransactionController(
				session, spendingCalculator, limitCalculator, chartCalculator, csvExporter
		);

		LimitController lc = new LimitController(session);
		NavigationController nc = new NavigationController(session);

		// wipe lim from prev weeks
		lc.checkAndApplyWeeklyReset();
		nc.requestSave();

		// BRIDGE
		AppContext.init(tc, lc, nc);

		// UI LAYER
		stage.setTitle("STRIVE");
		stage.setMinWidth(900);
		stage.setMinHeight(650);

		// UNSAVED CHANGE GUARD - warn before close if sesh "dirty"
		stage.setOnCloseRequest(e -> {
			if (nc.hasUnsavedChanges()) {
				javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
						javafx.scene.control.Alert.AlertType.CONFIRMATION);
				alert.initOwner(primaryStage);
				alert.initModality(Modality.WINDOW_MODAL);
				alert.initStyle(StageStyle.UNDECORATED);
				alert.setOnShown(ev -> {
					Stage s = (Stage) alert.getDialogPane().getScene().getWindow();
					alert.getDialogPane().getScene().setFill(Color.TRANSPARENT);
					s.setOnCloseRequest(Event::consume);
				});
				alert.setTitle("Unsaved Changes");
				alert.setHeaderText("You have unsaved changes.");
				alert.setContentText(
						"Are you sure you want to exit without saving? " + "None of the changes made will be saved.");
				alert.getDialogPane().getStylesheets().add(
						Objects.requireNonNull(
										STRIVEApp.class.getResource("/views/styles.css"))
								.toExternalForm());
				alert.showAndWait().ifPresent(response -> {
					if (response != javafx.scene.control.ButtonType.OK) {
						e.consume();
					}
				});
			}
		});

		navigateTo("dashboard");
		stage.show();
		System.out.println("[STRIVEApp] Ready.");
	}

	/** Loads and displays an FXML view by name.
	 *
	 * @param viewName "dashboard" or "charts"
	 */
	public static void navigateTo(String viewName) throws IOException {
		String fxmlPath = "/views/" + viewName + ".fxml";
		Parent root = FXMLLoader.load(
				Objects.requireNonNull(STRIVEApp.class.getResource(fxmlPath)));

		Scene scene;
		if (primaryStage.getScene() == null) {
			scene = new Scene(root, 1200, 750);
			scene.getStylesheets().add(
					Objects.requireNonNull(
							STRIVEApp.class.getResource("/views/styles.css"))
							.toExternalForm());
		} else {
			scene = primaryStage.getScene();
			scene.setRoot(root);
		}

		primaryStage.setScene(scene);
	}

	// expose the primary stage so controller can attach handlers
	public static Stage getPrimaryStage() { return primaryStage; }

	public static void main(String[] args) { launch(args); }
}
