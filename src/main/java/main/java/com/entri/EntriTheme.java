package main.java.com.entri;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

public final class EntriTheme {
    public static final String CORNSILK = "#FEFAE0";
    public static final String OLIVE = "#606C38";
    public static final String IVORY = "#FFEFCD";
    public static final String HOVER_OLIVE = "#738248";
    public static final String TEXT = "#252719";
    public static final String SECONDARY = "#6B705C";
    public static final String BORDER = "#D8D2BA";
    public static final String ERROR = "#8B1E1E";
    public static final String SUCCESS = "#2F6B36";
    public static final String BASE = "-fx-background-color: " + CORNSILK
            + "; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: " + TEXT + ";";

    private EntriTheme() {}

    public static void primary(Button button) {
        button.setStyle("-fx-background-color: " + OLIVE + "; -fx-text-fill: " + CORNSILK
                + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 10 18;");
    }

    public static void secondary(Button button) {
        button.setStyle("-fx-background-color: " + IVORY + "; -fx-text-fill: " + TEXT
                + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 9 16;");
    }

    public static void title(Label label) {
        label.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + TEXT + ";");
    }

    public static void muted(Label label) {
        label.setStyle("-fx-text-fill: " + SECONDARY + ";");
    }

    public static void table(TableView<?> table) {
        if (!table.getStyleClass().contains("entri-table")) {
            table.getStyleClass().add("entri-table");
        }
        table.setFixedCellSize(36);
        table.setPlaceholder(new Label("No records to display."));
    }
}
