package com.strive.ui;

import com.strive.STRIVEApp;
import com.strive.controller.NavigationController;
import com.strive.session.SessionListener;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

public abstract class BaseView implements Initializable, SessionListener {
    @FXML protected Button saveBtn;

    protected NavigationController navigationController;

    protected void syncSaveButton() {
        if (saveBtn == null || navigationController == null) return;
        if (navigationController.hasUnsavedChanges()) {
            saveBtn.getStyleClass().removeAll("btn-outline");
            if (!saveBtn.getStyleClass().contains("btn-primary"))
                saveBtn.getStyleClass().add("btn-primary");
        } else {
            saveBtn.getStyleClass().removeAll("btn-primary");
            if (!saveBtn.getStyleClass().contains("btn-outline"))
                saveBtn.getStyleClass().add("btn-outline");
        }
    }

    protected void applyStyles(DialogPane pane) {
        pane.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/views/styles.css")).toExternalForm());
    }

    protected void initDialog(javafx.scene.control.Dialog<?> d) {
        d.initOwner(STRIVEApp.getPrimaryStage());
        d.initModality(Modality.WINDOW_MODAL);
        d.initStyle(StageStyle.UNDECORATED);
        d.setOnShown(e -> {
            Stage s = (Stage) d.getDialogPane().getScene().getWindow();
            d.getDialogPane().getScene().setFill(Color.TRANSPARENT);

            s.setOnCloseRequest(Event::consume);
        });
    }

    protected void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        initDialog(a);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        applyStyles(a.getDialogPane());
        a.showAndWait();
    }

    protected void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        initDialog(a);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        applyStyles(a.getDialogPane());
        a.showAndWait();
    }
}
