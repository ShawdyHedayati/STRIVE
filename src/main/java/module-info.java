module com.cobra{
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens com.cobra to javafx.fxml;
    opens com.cobra.cache to javafx.fxml;
    opens com.cobra.types to javafx.base, javafx.fxml;

    exports com.cobra;
    exports com.cobra.types;
    exports com.cobra.cache;
}