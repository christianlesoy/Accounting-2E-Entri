package main.java.com.entri;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

public abstract class AccountingSection extends BorderPane {
    protected final MainApp app;
    protected final User user;

    protected AccountingSection(MainApp app, User user, String title) {
        this.app = app;
        this.user = user;
        setPadding(new Insets(24));
        Label heading = new Label(title);
        EntriTheme.title(heading);
        setTop(heading);
    }

    public abstract void reload();
}
