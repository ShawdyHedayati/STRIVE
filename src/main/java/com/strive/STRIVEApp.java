package com.strive;

import com.strive.config.AppContext;
import com.strive.util.CSVExporter;
import com.strive.bll.ChartCalculator;
import com.strive.bll.LimitCalculator;
import com.strive.bll.SpendingCalculator;
import com.strive.controller.LimitController;
import com.strive.controller.NavigationController;
import com.strive.controller.TransactionController;
import com.strive.model.dao.LimitDAO;
import com.strive.model.dao.TransactionDAO;
import com.strive.session.SessionManager;

import javafx.application.Application;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 *  COMMENTS ASSISTED BY CLAUDE.AI
 */

/**
 * Application entry point and top-level compositor for the CoBRA / STRIVE
 * budget application.
 *
 * Startup wiring order (must not be reordered):
 *
 *   Model layer — resolve the SQLite database path, construct the
 *   DAOs ({@link TransactionDAO}, {@link LimitDAO}).
 *   Session layer — construct {@link SessionManager}, which
 *   immediately loads both tables from the database into memory.
 *   BLL layer — construct the stateless calculators
 *   ({@link SpendingCalculator}, {@link LimitCalculator},
 *   {@link ChartCalculator}, {@link CSVExporter}).
 *   Controller layer — construct {@link TransactionController},
 *   {@link LimitController}, and {@link NavigationController}, each sharing
 *   the same {@link SessionManager} instance.
 *   Weekly reset — check for stale limits from prior weeks and
 *   flush any deletions before the UI appears.
 *   AppContext bridge — register the three controllers in
 *   {@link AppContext} so FXML views can retrieve them during
 *   {@code initialize()}.
 *   UI layer — configure the primary stage and load the initial
 *   Dashboard FXML view via {@link #navigateTo(String)}.
 *
 * Navigation: Switching between Dashboard and Charts is done by
 * calling {@link #navigateTo(String)} with {@code "dashboard"} or
 * {@code "charts"}. The method swaps the scene's root node rather than
 * creating a new {@link Scene} each time, preserving the stylesheet.
 *
 * Known issues / TODOs:
 *   On macOS fullscreen, modal dialogs (exit-without-saving, add/edit
 *   transaction) are detected as separate windows rather than popups.
 *   Database path is resolved relative to {@code user.dir} — needs to
 *   be updated to a packaged-distribution path before release.
 *   Application logo and icons not yet added.
 *
 * @see AppContext
 * @see SessionManager
 */

public class STRIVEApp extends Application {
	/**
	 * The application's single primary {@link Stage}, stored statically so
	 * that views and dialogs can reference it via {@link #getPrimaryStage()}
	 * without holding a direct reference to this class.
	 */
	private static Stage primaryStage;

	// -------------------------------------------------------------------------
	// Application lifecycle
	// -------------------------------------------------------------------------

	/**
	 * JavaFX application entry point. Called by the JavaFX runtime after
	 * {@link #main(String[])} invokes {@link #launch(String[])}.
	 *
	 * Constructs the full object graph (model → session → BLL → controllers),
	 * runs the weekly limit reset, populates {@link AppContext}, configures
	 * the primary stage, and loads the initial Dashboard view.
	 *
	 * @param stage the primary stage provided by the JavaFX runtime
	 * @throws Exception if any layer fails to initialize (e.g. database
	 *                   connection error, missing FXML file)
	 */
	@Override
	public void start(Stage stage) throws Exception {
		primaryStage = stage;

		// ── 1. Model layer: resolve DB path and construct DAOs ────────────────

		// Resolve the database file relative to the working directory.
		File topDir = new File(System.getProperty("user.dir"));
		File dbFile = new File(topDir,
				"/src/main/resources/strive_test.db");
		String dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
		System.out.println("[STRIVEApp] DB: " + dbFile.getAbsolutePath());

		TransactionDAO transactionDAO = new TransactionDAO(dbUrl);
		LimitDAO limitDAO = new LimitDAO(dbUrl);

		// ── 2. Session layer: load initial state from the database ────────────

		// SessionManager constructor calls fetchAll() on both DAOs immediately,
		// so in-memory state is fully hydrated before any controller is created
		SessionManager session = new SessionManager(transactionDAO, limitDAO);

		// ── 3. BLL layer: stateless calculators (no shared state) ─────────────

		SpendingCalculator spendingCalculator = new SpendingCalculator();
		LimitCalculator limitCalculator = new LimitCalculator();
		ChartCalculator chartCalculator= new ChartCalculator();
		CSVExporter csvExporter = new CSVExporter();

		// ── 4. Controller layer: all three share the same session instance ─────

		TransactionController tc = new TransactionController(
				session, spendingCalculator, limitCalculator, chartCalculator, csvExporter
		);

		LimitController lc = new LimitController(session);
		NavigationController nc = new NavigationController(session);

		// ── 5. Weekly reset: remove stale limits from prior weeks ─────────────

		// Check if any limits were created before the current Monday; if so, delete them
		lc.checkAndApplyWeeklyReset();

		// Flush the reset deletions to the database immediately, before the UI appears,
		// so the user never sees stale limits on startup
		nc.requestSave();

		// ── 6. AppContext bridge: make controllers available to FXML views ─────

		AppContext.init(tc, lc, nc);

		// ── 7. UI layer: configure stage and load the initial view ────────────

		stage.setTitle("STRIVE");
		stage.setMinWidth(900); // prevent the layout from collapsing below usable size
		stage.setMinHeight(650);

		// Unsaved-changes guard: intercept the OS close request and warn the user
		// if there are pending commands not yet flushed to the database
		stage.setOnCloseRequest(e -> {
			if (nc.hasUnsavedChanges()) {
				javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
						javafx.scene.control.Alert.AlertType.CONFIRMATION);

				// Configure the dialog to match CoBRA's custom modal style
				alert.initOwner(primaryStage);
				alert.initModality(Modality.WINDOW_MODAL);
				alert.initStyle(StageStyle.UNDECORATED);

				alert.setOnShown(ev -> {
					Stage s = (Stage) alert.getDialogPane().getScene().getWindow();
					alert.getDialogPane().getScene().setFill(Color.TRANSPARENT); // transparent bg
					s.setOnCloseRequest(Event::consume); // suppress the OS close button on the dialog
				});

				alert.setTitle("Unsaved Changes");
				alert.setHeaderText("You have unsaved changes.");
				alert.setContentText(
						"Are you sure you want to exit without saving? " + "None of the changes made will be saved.");

				// Apply the shared CoBRA stylesheet so the dialog matches the rest of the UI
				alert.getDialogPane().getStylesheets().add(
						Objects.requireNonNull(
										STRIVEApp.class.getResource("/views/styles.css"))
								.toExternalForm());

				alert.showAndWait().ifPresent(response -> {
					if (response != javafx.scene.control.ButtonType.OK) {
						e.consume();// user chose Cancel — abort the close request
					}
					// If OK: the close request proceeds normally and the app exits
				});
			}
			// No unsaved changes — close request proceeds without interruption
		});

		navigateTo("dashboard"); // load the Dashboard FXML as the initial view
		stage.show();
		System.out.println("[STRIVEApp] Ready.");
	}

	// -------------------------------------------------------------------------
	// Navigation
	// -------------------------------------------------------------------------

	/**
	 * Loads and displays an FXML view by name, swapping the scene's root node.
	 *
	 * On the first call (no existing scene), a new {@link Scene} is created
	 * at 1200×750 and the shared stylesheet is applied. On subsequent calls,
	 * only the root node is replaced — the scene and its stylesheet are reused,
	 * avoiding redundant stylesheet loads and preserving the stage's dimensions.
	 *
	 * Each loaded FXML's {@code initialize()} method retrieves its backend
	 * controllers from {@link AppContext} immediately after construction.
	 *
	 * @param viewName the view to load; must be either {@code "dashboard"} or
	 *                 {@code "charts"} (maps to {@code /views/{viewName}.fxml}
	 *                 on the classpath)
	 * @throws IOException              if the FXML file cannot be found or parsed
	 * @throws NullPointerException     if the resource path does not exist on the classpath
	 */
	public static void navigateTo(String viewName) throws IOException {
		String fxmlPath = "/views/" + viewName + ".fxml";

		// FXMLLoader.load() instantiates the FXML controller via reflection and
		// calls initialize() on it — AppContext must be populated before this line
		Parent root = FXMLLoader.load(
				Objects.requireNonNull(STRIVEApp.class.getResource(fxmlPath)));

		Scene scene;

		if (primaryStage.getScene() == null) {
			// First navigation: create a new scene with the stylesheet applied
			scene = new Scene(root, 1200, 750);
			scene.getStylesheets().add(
					Objects.requireNonNull(
							STRIVEApp.class.getResource("/views/styles.css"))
							.toExternalForm());
		} else {
			// Subsequent navigation: reuse the existing scene, swap only the root
			// This preserves the stylesheet and avoids reinitialising the scene graph
			scene = primaryStage.getScene();
			scene.setRoot(root);
		}

		primaryStage.setScene(scene);
	}

	// -------------------------------------------------------------------------
	// Static accessors
	// -------------------------------------------------------------------------

	/**
	 * Returns the application's primary {@link Stage}.
	 *
	 * Used by views and dialogs that need to attach to or position
	 * themselves relative to the main window (e.g. popup tooltips,
	 * modal dialogs, and file choosers).
	 *
	 * @return the primary stage; {@code null} before {@link #start(Stage)} is called
	 */
	public static Stage getPrimaryStage() { return primaryStage; }

	// -------------------------------------------------------------------------
	// Main
	// -------------------------------------------------------------------------

	/**
	 * JVM entry point. Delegates to {@link Application#launch(String[])} which
	 * initializes the JavaFX runtime and calls {@link #start(Stage)}.
	 *
	 * @param args command-line arguments (currently unused)
	 */
	public static void main(String[] args) { launch(args); }
}
