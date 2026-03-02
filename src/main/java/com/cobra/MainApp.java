package com.cobra;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start (Stage stage) throws Exception {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/views/dashboard.fxml"));
        Scene scene = new Scene(fxml.load(), 900, 600);
        stage.setTitle("Budget Tracker");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}