package com.cobra;

// import com.cobra.types.*;

//import java.util.ArrayList;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

	@Override
	public void start(Stage stage) throws Exception {
		System.out.println("[MainApp] start() called");
		FXMLLoader fxml = new FXMLLoader(getClass().getResource("/views/dashboard.fxml"));
		System.out.println("[MainApp] fxml loaded");
		Scene scene = new Scene(fxml.load(), 900, 600);
		System.out.println("[MainApp] scene created");
		DashboardView view = fxml.getController();
		Controller controller = new Controller();
		System.out.println("[MainApp] controller created");
		view.setController(controller);
		stage.setTitle("STRIVE");
		stage.setScene(scene);
		stage.show();
		System.out.println("[MainApp] stage shown");
	}

	public static void main(String[] args) {
		System.out.println("[MainApp] main() called");
		launch();
	}
}
