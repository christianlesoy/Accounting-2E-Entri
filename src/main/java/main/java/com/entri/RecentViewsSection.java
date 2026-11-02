package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RecentViewsSection extends AccountingSection {
    private final TableView<RecentView> table = new TableView<>();
    private final Consumer<RecentView> openAction;
    private final TextField search = new TextField();
    private List<RecentView> allViews = new ArrayList<>();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public RecentViewsSection(MainApp app, User user, Consumer<RecentView> openAction) {
        super(app, user, "Recent Views",
                "Reopen accounting pages and records you have recently accessed.");
        this.openAction = openAction;
        addColumn("Name", RecentView::getName, 430, 180);
        addColumn("Record type", RecentViewsSection::readableType, 220, 130);
        addColumn("Last viewed", view -> DATE_FORMAT.format(view.getViewedAt()), 210, 145);
        EntriTheme.table(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        search.setPromptText("Search recent views");
        search.textProperty().addListener((observable, previous, current) -> applyFilter());
        Button open = new Button("Open selected");
        Button refresh = new Button("Refresh");
        EntriTheme.primary(open);
        open.setOnAction(e -> {
            RecentView selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) app.showError("Recent Views", "Select an item first.");
            else openAction.accept(selected);
        });
        refresh.setOnAction(e -> reload());
        TableColumn<RecentView, Void> action = new TableColumn<>("Action");
        action.setSortable(false);
        action.setPrefWidth(110);
        action.setMinWidth(90);
        action.setCellFactory(column -> new TableCell<RecentView, Void>() {
            private final Button openButton = new Button("Open");
            {
                EntriTheme.secondary(openButton);
                openButton.setOnAction(event -> {
                    RecentView selected = getTableRow() == null ? null : getTableRow().getItem();
                    if (selected != null) openAction.accept(selected);
                });
            }
            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : openButton);
            }
        });
        table.getColumns().add(action);
        table.setRowFactory(view -> {
            TableRow<RecentView> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openAction.accept(row.getItem());
            });
            return row;
        });
        FlowPane actions = new FlowPane(10, 8, open, refresh, search);
        search.setMinWidth(160);
        search.setPrefWidth(240);
        search.setMaxWidth(320);
        actions.setPadding(new Insets(6, 0, 8, 0));
        table.setPlaceholder(new Label("Items you open will appear here. No recent views yet."));
        VBox content = new VBox(10, actions, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    private void addColumn(String name, java.util.function.Function<RecentView, String> value,
                           double width, double minWidth) {
        TableColumn<RecentView, String> column = new TableColumn<>(name);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(minWidth);
        table.getColumns().add(column);
    }

    @Override public void reload() {
        table.setPlaceholder(new Label("Loading Recent Views..."));
        app.async(() -> app.accounting().recentViews(user.getId()), views -> {
            allViews = views;
            applyFilter();
        }, ex -> app.showError("Could not load Recent Views", MainApp.safeMessage(ex)));
    }

    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    private void applyFilter() {
        String query = search.getText() == null
                ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        List<RecentView> matches = new ArrayList<>();
        for (RecentView view : allViews) {
            if (view.getName().toLowerCase(java.util.Locale.ROOT).contains(query)
                    || readableType(view).toLowerCase(java.util.Locale.ROOT).contains(query)) {
                matches.add(view);
            }
        }
        table.setItems(FXCollections.observableArrayList(matches));
        if (matches.isEmpty() && !query.isEmpty()) {
            table.setPlaceholder(new Label("No recent items match your search. Clear the search to see all recent views."));
        } else if (matches.isEmpty()) {
            table.setPlaceholder(new Label("Items you open will appear here. No recent views yet."));
        }
    }

    private static String readableType(RecentView view) {
        if (view.isBalanceSheetReport()) {
            return "Balance Sheet";
        }
        return readableType(view.getType());
    }

    static String readableType(String type) {
        if ("JOURNAL_ENTRY".equals(type)) return "Journal Entry";
        if ("LEDGER_ACCOUNT".equals(type)) return "Ledger Account";
        if ("WORKSHEET".equals(type)) return "12-Column Worksheet";
        return type;
    }
}
