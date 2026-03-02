module com.cobra{
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens com.cobra to javafx.fxml;
    opens com.cobra.controllers to javafx.fxml;
    opens com.cobra.models to javafx.base;

    exports com.cobra;
}