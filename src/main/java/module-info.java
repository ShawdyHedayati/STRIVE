module com.strive {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens com.strive to javafx.fxml;
    opens com.strive.ui to javafx.fxml;
    opens com.strive.model to javafx.base, javafx.fxml;
    opens com.strive.session to javafx.base;

    exports com.strive;
    exports com.strive.ui;
    exports com.strive.controller;
    exports com.strive.session;
    exports com.strive.bll;
    exports com.strive.model;
}