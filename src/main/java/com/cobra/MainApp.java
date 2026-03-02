package com.cobra;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start (Stage stage) throws Exception {
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("/views/dashboard.fxml"));
    }
}