package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class RecentViewsSection extends AccountingSection {
    private final TableView<RecentView> table = new TableView<>();
    private final Consumer<RecentView> openAction;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public RecentViewsSection(MainApp app, User user, Consumer<RecentView> openAction) {
        super(app, user, "Recent Views");
        this.openAction = openAction;
        addColumn("Item", RecentView::getName, 330);
        addColumn("Type", view -> readableType(view.getType()), 180);
        addColumn("Last viewed", view -> DATE_FORMAT.format(view.getViewedAt()), 190);
        Button open = new Button("Open selected");
        Button refresh = new Button("Refresh");
        EntriTheme.primary(open);
        open.setOnAction(e -> {
            RecentView selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) app.showError("Recent Views", "Select an item first.");
            else openAction.accept(selected);
        });
        refresh.setOnAction(e -> reload());
        table.setRowFactory(view -> {
            TableRow<RecentView> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openAction.accept(row.getItem());
            });
            return row;
        });
        HBox actions = new HBox(10, open, refresh);
        actions.setPadding(new Insets(16, 0, 10, 0));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Items you open will appear here. No recent views yet."));
        VBox content = new VBox(10, actions, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    private void addColumn(String name, java.util.function.Function<RecentView, String> value, double width) {
        TableColumn<RecentView, String> column = new TableColumn<>(name);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }

    @Override public void reload() {
        table.setPlaceholder(new Label("Loading Recent Views..."));
        app.async(() -> app.accounting().recentViews(user.getId()), views -> {
            table.setItems(FXCollections.observableArrayList(views));
        }, ex -> app.showError("Could not load Recent Views", MainApp.safeMessage(ex)));
    }

    static String readableType(String type) {
        if ("JOURNAL_ENTRY".equals(type)) return "Journal Entry";
        if ("LEDGER_ACCOUNT".equals(type)) return "Ledger Account";
        if ("WORKSHEET".equals(type)) return "12-Column Worksheet";
        return type;
    }
}
