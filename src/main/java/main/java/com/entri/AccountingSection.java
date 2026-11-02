package main.java.com.entri;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public abstract class AccountingSection extends BorderPane {
    protected final MainApp app;
    protected final User user;
    private final Label heading;
    private final Label subtitle;

    protected AccountingSection(MainApp app, User user, String title) {
        this(app, user, title, "");
    }

    protected AccountingSection(MainApp app, User user, String title, String description) {
        this.app = app;
        this.user = user;
        setPadding(new Insets(18, 24, 18, 24));
        heading = new Label(title);
        EntriTheme.title(heading);
        heading.setWrapText(true);
        subtitle = new Label(description);
        subtitle.setWrapText(true);
        EntriTheme.muted(subtitle);
        VBox pageHeading = new VBox(6, heading, subtitle);
        pageHeading.setPadding(new Insets(0, 0, 16, 0));
        setTop(pageHeading);
    }

    protected final void setSectionHeading(String title, String description) {
        heading.setText(title);
        subtitle.setText(description == null ? "" : description);
    }

    protected final void exportCsv(String defaultFileName, List<String> headers,
                                   List<? extends List<?>> rows) {
        try {
            CsvWriter.validate(headers, rows);
        } catch (IllegalArgumentException ex) {
            app.showError("Invalid CSV data", ex.getMessage());
            return;
        }
        if (rows == null || rows.isEmpty()) {
            app.showError("Export CSV", "There is no data to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        String fileName = defaultFileName == null || defaultFileName.isBlank()
                ? "export.csv" : defaultFileName;
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            fileName += ".csv";
        }
        chooser.setInitialFileName(fileName);
        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Replace the existing file?\n\n" + file.getAbsolutePath(),
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setTitle("Replace file");
            confirm.setHeaderText("A file with this name already exists.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        try {
            CsvWriter.write(file.toPath(), headers, rows);
        } catch (IllegalArgumentException ex) {
            app.showError("Invalid CSV data", ex.getMessage());
            return;
        } catch (IOException | SecurityException ex) {
            app.showError("Export CSV", "Could not export the CSV file. " + MainApp.safeMessage(ex));
            return;
        }

        Alert success = new Alert(Alert.AlertType.INFORMATION,
                "CSV exported successfully:\n" + file.getAbsolutePath(), ButtonType.OK);
        success.setTitle("Export CSV");
        success.setHeaderText("Export complete");
        success.showAndWait();
    }

    public abstract void reload();
}
