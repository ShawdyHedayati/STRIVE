module com.cobra {
	requires javafx.controls;
	requires javafx.fxml;
	requires java.sql;
	// requires org.xerial.sqlitedbc; This may or may not be needed

	opens com.cobra to javafx.fxml;
	opens com.cobra.types to javafx.base;

	exports com.cobra;
}
