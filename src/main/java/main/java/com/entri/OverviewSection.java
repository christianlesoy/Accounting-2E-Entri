package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class OverviewSection extends AccountingSection {
    private final Consumer<String> navigate;
    private final Consumer<JournalEntry> openEntry;
    private final Runnable addEntry;
    private final VBox cards = new VBox(16);
    private final Label greeting = new Label();
    private final Label recordSummary = new Label("Loading your accounting records...");
    private GridPane summaryGrid;
    private GridPane previewGrid;
    private List<Node> summaryCards = List.of();
    private List<Node> previewCards = List.of();

    public OverviewSection(MainApp app, User user, Consumer<String> navigate,
                           Consumer<JournalEntry> openEntry, Runnable addEntry) {
        super(app, user, "Dashboard");
        this.navigate = navigate;
        this.openEntry = openEntry;
        this.addEntry = addEntry;
        Label workspaceLabel = new Label("YOUR WORKSPACE");
        workspaceLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.SECONDARY + ";");
        greeting.setText("Welcome back, " + user.getUsername() + "!");
        greeting.setWrapText(true);
        greeting.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + EntriTheme.OLIVE + ";");
        EntriTheme.muted(recordSummary);
        Label welcomeText = new Label("Your accounting workspace is ready.");
        welcomeText.setStyle("-fx-font-size: 15px; -fx-text-fill: " + EntriTheme.TEXT + ";");
        VBox welcomeCopy = new VBox(9, workspaceLabel, greeting, welcomeText, recordSummary);
        BorderPane banner = new BorderPane();
        banner.setLeft(welcomeCopy);
        banner.setPadding(new Insets(26, 28, 26, 28));
        banner.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-background-radius: 18;"
                + " -fx-border-color: " + EntriTheme.BORDER + "; -fx-border-radius: 18;");
        cards.setPadding(new Insets(4, 0, 12, 0));
        cards.widthProperty().addListener((observable, oldWidth, newWidth) -> rearrangeCards());
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox content = new VBox(16, banner, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        setCenter(content);
    }

    @Override public void reload() {
        cards.getChildren().clear();
        Label loading = new Label("Loading accounting previews...");
        cards.getChildren().add(loading);
        app.async(() -> new OverviewData(
                app.accounting().entries(user.getId(), ""),
                app.accounting().accounts(user.getId()),
                app.accounting().recentViews(user.getId())), data -> {
            cards.getChildren().clear();
            recordSummary.setText(data.entries.size() + " journal entries  ·  "
                    + data.accounts.size() + " ledger accounts  ·  " + data.recent.size() + " recent views");
            summaryGrid = new GridPane();
            summaryGrid.setHgap(14);
            summaryGrid.setVgap(14);
            summaryCards = List.of(
                    summaryCard("Journal entries", String.valueOf(data.entries.size()), "View Journal Entry", "journal"),
                    summaryCard("Ledger accounts", String.valueOf(data.accounts.size()), "Open General Ledger", "ledger"),
                    summaryCard("Recent views", String.valueOf(data.recent.size()), "View Recent Views", "recent"));
            previewGrid = new GridPane();
            previewGrid.setHgap(14);
            previewGrid.setVgap(14);
            previewCards = List.of(createJournalPreview(data.entries),
                    createLedgerPreview(data.accounts), createWorksheetPreview(data.entries),
                    createRecentPreview(data.recent));
            for (Node node : previewCards) {
                GridPane.setHgrow(node, Priority.ALWAYS);
                GridPane.setVgrow(node, Priority.ALWAYS);
            }
            VBox.setVgrow(previewGrid, Priority.ALWAYS);
            cards.getChildren().addAll(summaryGrid, previewGrid);
            rearrangeCards();
        }, ex -> {
            cards.getChildren().clear();
            cards.getChildren().add(errorCard("Dashboard data could not be loaded",
                    MainApp.safeMessage(ex), "Refresh dashboard", this::reload));
        });
    }

    private VBox summaryCard(String title, String value, String action, String target) {
        VBox card = cardBox();
        Label titleLabel = new Label(title);
        EntriTheme.muted(titleLabel);
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + EntriTheme.OLIVE + ";");
        Button button = new Button(action);
        EntriTheme.secondary(button);
        button.setOnAction(e -> navigate.accept(target));
        card.getChildren().addAll(titleLabel, valueLabel, button);
        return card;
    }

    private VBox createJournalPreview(List<JournalEntry> entries) {
        VBox card = cardBox();
        Button viewAll = new Button("View All");
        Button add = new Button("Add Entry");
        EntriTheme.secondary(viewAll);
        EntriTheme.primary(add);
        viewAll.setOnAction(e -> navigate.accept("journal"));
        add.setOnAction(e -> {
            navigate.accept("journal");
            addEntry.run();
        });
        HBox actions = new HBox(8, viewAll, add);
        TableView<JournalEntry> preview = new TableView<>();
        EntriTheme.table(preview);
        preview.setMinHeight(150);
        preview.setPrefHeight(220);
        VBox.setVgrow(preview, Priority.ALWAYS);
        preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        preview.getColumns().add(column("Date", entry -> entry.getDate().toString()));
        preview.getColumns().add(column("Particulars", JournalEntry::getParticulars));
        TableColumn<JournalEntry, String> currency = column("Currency", entry -> app.currencySymbol());
        currency.setPrefWidth(55);
        Label currencyHeading = new Label("Currency");
        currencyHeading.setTooltip(new Tooltip(
                "Display preference only. Saved entry currency codes remain unchanged; no conversion is performed."));
        currency.setText(null);
        currency.setGraphic(currencyHeading);
        preview.getColumns().add(currency);
        preview.getColumns().add(column("Debit", entry -> fmt(entry.getDebitTotal())));
        preview.getColumns().add(column("Credit", entry -> fmt(entry.getCreditTotal())));
        preview.setItems(FXCollections.observableArrayList(firstEntries(entries, 5)));
        preview.setPlaceholder(new Label("No saved journal entries."));
        preview.setRowFactory(view -> {
            TableRow<JournalEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openEntry.accept(row.getItem());
            });
            return row;
        });
        card.getChildren().addAll(sectionHeader("Journal Entry", actions), preview,
                totalsByCurrency(entries));
        return card;
    }

    private VBox createLedgerPreview(List<Account> accounts) {
        VBox card = cardBox();
        Button open = new Button("View All");
        EntriTheme.secondary(open);
        open.setOnAction(e -> navigate.accept("ledger"));
        TableView<AccountingRepository.LedgerLine> preview = new TableView<>();
        EntriTheme.table(preview);
        preview.setMinHeight(150);
        preview.setPrefHeight(220);
        VBox.setVgrow(preview, Priority.ALWAYS);
        preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<AccountingRepository.LedgerLine, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().date.toString()));
        TableColumn<AccountingRepository.LedgerLine, String> account = new TableColumn<>("Account");
        account.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().account));
        TableColumn<AccountingRepository.LedgerLine, String> explanation = new TableColumn<>("Explanation");
        explanation.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().explanation));
        TableColumn<AccountingRepository.LedgerLine, String> currency = new TableColumn<>("Currency");
        currency.setCellValueFactory(d -> new ReadOnlyStringWrapper(app.currencySymbol()));
        currency.setPrefWidth(55);
        Label currencyHeading = new Label("Currency");
        currencyHeading.setTooltip(new Tooltip(
                "Display preference only. Amounts are not converted."));
        currency.setText(null);
        currency.setGraphic(currencyHeading);
        TableColumn<AccountingRepository.LedgerLine, String> debit = new TableColumn<>("Debit");
        debit.setCellValueFactory(d -> new ReadOnlyStringWrapper(fmt(d.getValue().debit)));
        TableColumn<AccountingRepository.LedgerLine, String> credit = new TableColumn<>("Credit");
        credit.setCellValueFactory(d -> new ReadOnlyStringWrapper(fmt(d.getValue().credit)));
        TableColumn<AccountingRepository.LedgerLine, String> balance = new TableColumn<>("Running Balance");
        balance.setCellValueFactory(d -> new ReadOnlyStringWrapper(fmt(d.getValue().balance)));
        preview.getColumns().add(date);
        preview.getColumns().add(account);
        preview.getColumns().add(explanation);
        preview.getColumns().add(currency);
        preview.getColumns().add(debit);
        preview.getColumns().add(credit);
        preview.getColumns().add(balance);
        preview.setPlaceholder(new Label(accounts.isEmpty()
                ? "No ledger accounts yet. Your chart of accounts will appear here."
                : "No transactions posted to the first ledger account yet."));
        card.getChildren().addAll(sectionHeader("Ledger", open), preview);
        if (!accounts.isEmpty()) {
            Account first = accounts.get(0);
            app.async(() -> app.accounting().ledger(user.getId(), first.getId()), rows -> {
                preview.setItems(FXCollections.observableArrayList(firstLedgerRows(rows, 5)));
            }, ex -> preview.setPlaceholder(new Label(MainApp.safeMessage(ex))));
        }
        return card;
    }

    private VBox createWorksheetPreview(List<JournalEntry> entries) {
        VBox card = cardBox();
        Label text = new Label("A live report derived from your saved journal entries and adjustments.");
        EntriTheme.muted(text);
        Button open = new Button("View 12-Column Worksheet");
        EntriTheme.secondary(open);
        open.setOnAction(e -> navigate.accept("worksheet"));
        TableView<WorksheetCalculator.DisplayRow> preview = new TableView<>();
        EntriTheme.table(preview);
        preview.setMinHeight(210);
        preview.setPrefHeight(300);
        VBox.setVgrow(preview, Priority.ALWAYS);
        preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<WorksheetCalculator.DisplayRow, String> account = new TableColumn<>("Account");
        account.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name));
        account.setPrefWidth(200);
        account.setMinWidth(170);
        preview.getColumns().add(account);
        String[] groups = {"Trial Balance", "Adjustments", "Adjusted Trial Balance",
                "Income Statement", "Owner's Equity", "Balance Sheet"};
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            TableColumn<WorksheetCalculator.DisplayRow, String> group =
                    new TableColumn<>(groups[groupIndex]);
            group.getColumns().add(worksheetAmountColumn("Debit", groupIndex * 2));
            group.getColumns().add(worksheetAmountColumn("Credit", groupIndex * 2 + 1));
            preview.getColumns().add(group);
        }
        preview.setPlaceholder(new Label(entries.isEmpty()
                ? "No journal records to include in a worksheet yet." : "Loading worksheet preview..."));
        ScrollPane tableScroll = new ScrollPane(preview);
        tableScroll.setFitToHeight(true);
        tableScroll.setPannable(true);
        tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tableScroll.setMinHeight(210);
        tableScroll.setPrefHeight(300);
        tableScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        preview.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(1510, tableScroll.getViewportBounds().getWidth()),
                tableScroll.viewportBoundsProperty()));
        card.setMinHeight(360);
        card.getChildren().addAll(sectionHeader("12-Column Worksheet", open), text, tableScroll);
        app.async(() -> app.accounting().worksheet(user.getId()), data -> {
            WorksheetCalculator.Result result = WorksheetCalculator.calculate(data);
            preview.setItems(FXCollections.observableArrayList(result.rows));
        }, ex -> preview.setPlaceholder(new Label(MainApp.safeMessage(ex))));
        return card;
    }

    private TableColumn<WorksheetCalculator.DisplayRow, String> worksheetAmountColumn(
            String heading, int valueIndex) {
        TableColumn<WorksheetCalculator.DisplayRow, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(fmt(data.getValue().values[valueIndex])));
        column.setPrefWidth(110);
        column.setMinWidth(85);
        column.setCellFactory(ignored -> new TableCell<WorksheetCalculator.DisplayRow, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            }
        });
        return column;
    }

    private VBox createRecentPreview(List<RecentView> recent) {
        VBox card = cardBox();
        Button all = new Button("View All");
        Button refresh = new Button("Refresh");
        EntriTheme.secondary(all);
        EntriTheme.secondary(refresh);
        all.setOnAction(e -> navigate.accept("recent"));
        refresh.setOnAction(e -> reload());
        TableView<RecentView> table = new TableView<>();
        EntriTheme.table(table);
        table.setMinHeight(180);
        table.setPrefHeight(240);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<RecentView, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        name.setPrefWidth(360);
        name.setMinWidth(180);
        TableColumn<RecentView, String> type = new TableColumn<>("Record Type");
        type.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                RecentViewsSection.readableType(data.getValue().getType())));
        type.setPrefWidth(200);
        type.setMinWidth(130);
        TableColumn<RecentView, String> viewed = new TableColumn<>("Last Viewed");
        viewed.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(data.getValue().getViewedAt())));
        viewed.setPrefWidth(190);
        viewed.setMinWidth(145);
        TableColumn<RecentView, Void> action = new TableColumn<>("Action");
        action.setSortable(false);
        action.setPrefWidth(100);
        action.setMinWidth(90);
        action.setCellFactory(ignored -> new TableCell<RecentView, Void>() {
            private final Button open = new Button("Open");
            {
                EntriTheme.secondary(open);
                open.setOnAction(event -> {
                    RecentView selected = getTableRow() == null ? null : getTableRow().getItem();
                    if (selected != null) app.openRecent(selected);
                });
            }
            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : open);
            }
        });
        table.getColumns().add(name);
        table.getColumns().add(type);
        table.getColumns().add(viewed);
        table.getColumns().add(action);
        table.setItems(FXCollections.observableArrayList(firstRecentViews(recent, 8)));
        table.setPlaceholder(new Label("You have not opened any records yet."));
        table.setRowFactory(view -> {
            TableRow<RecentView> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) app.openRecent(row.getItem());
            });
            return row;
        });
        card.setMinHeight(300);
        card.getChildren().addAll(sectionHeader("Recent Views", new HBox(8, all, refresh)), table);
        return card;
    }

    private List<RecentView> firstRecentViews(List<RecentView> views, int limit) {
        List<RecentView> result = new ArrayList<>();
        for (int index = 0; index < views.size() && index < limit; index++) result.add(views.get(index));
        return result;
    }

    private VBox errorCard(String title, String message, String action, Runnable refresh) {
        VBox box = cardBox();
        Label header = new Label(title);
        header.setStyle("-fx-text-fill: " + EntriTheme.ERROR + "; -fx-font-weight: bold;");
        Label detail = new Label(message);
        detail.setWrapText(true);
        Button button = new Button(action);
        button.setOnAction(e -> refresh.run());
        cardChildren(box, header, detail, button);
        return box;
    }

    private HBox sectionHeader(String title, javafx.scene.Node actions) {
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + EntriTheme.TEXT + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, label, spacer, actions);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return header;
    }

    private Label totalsByCurrency(List<JournalEntry> entries) {
        List<JournalEntry> shown = firstEntries(entries, 5);
        Map<String, BigDecimal[]> sums = new TreeMap<>();
        for (JournalEntry entry : shown) {
            BigDecimal[] value = sums.get(entry.getCurrency());
            if (value == null) {
                value = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                sums.put(entry.getCurrency(), value);
            }
            value[0] = value[0].add(entry.getDebitTotal());
            value[1] = value[1].add(entry.getCreditTotal());
        }
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> item : sums.entrySet()) {
            BigDecimal[] value = item.getValue();
            values.add(item.getKey() + " debit " + fmt(value[0]) + " / credit " + fmt(value[1]));
        }
        Label result = new Label("Preview totals by currency: "
                + (values.isEmpty() ? "0" : String.join("     ", values)));
        EntriTheme.muted(result);
        return result;
    }

    private List<JournalEntry> firstEntries(List<JournalEntry> entries, int limit) {
        List<JournalEntry> result = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            result.add(entries.get(i));
        }
        return result;
    }

    private List<AccountingRepository.LedgerLine> firstLedgerRows(
            List<AccountingRepository.LedgerLine> rows, int limit) {
        List<AccountingRepository.LedgerLine> result = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            result.add(rows.get(i));
        }
        return result;
    }

    private <T> TableColumn<JournalEntry, String> column(String title,
                                                         java.util.function.Function<JournalEntry, String> value) {
        TableColumn<JournalEntry, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new ReadOnlyStringWrapper(value.apply(d.getValue())));
        return c;
    }

    private static VBox cardBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setMinHeight(240);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: "
                + EntriTheme.BORDER + "; -fx-border-radius: 12;");
        return box;
    }

    private static void arrangeGrid(GridPane grid, List<Node> items, int columns, boolean growRows) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            constraint.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraint);
        }

        for (int index = 0; index < items.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            grid.add(items.get(index), column, row);
        }
        if (growRows) {
            int rowCount = (items.size() + columns - 1) / columns;
            for (int row = 0; row < rowCount; row++) {
                RowConstraints constraint = new RowConstraints();
                constraint.setVgrow(Priority.ALWAYS);
                constraint.setMinHeight(250);
                grid.getRowConstraints().add(constraint);
            }
        }
    }

    private void rearrangeCards() {
        if (summaryGrid == null || previewGrid == null || cards.getWidth() <= 0) return;
        int summaryColumns = cards.getWidth() >= 1050 ? 3 : cards.getWidth() >= 700 ? 2 : 1;
        int previewColumns = cards.getWidth() >= 900 ? 2 : 1;
        arrangeGrid(summaryGrid, summaryCards, summaryColumns, false);
        arrangePreviewGrid(previewGrid, previewCards, previewColumns);
    }

    private static void arrangePreviewGrid(GridPane grid, List<Node> cards, int columns) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        grid.getRowConstraints().clear();
        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            constraint.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraint);
        }

        if (columns == 1) {
            for (int row = 0; row < cards.size(); row++) {
                GridPane.setHgrow(cards.get(row), Priority.ALWAYS);
                GridPane.setVgrow(cards.get(row), Priority.ALWAYS);
                grid.add(cards.get(row), 0, row);
            }
        } else {
            for (int column = 0; column < 2; column++) {
                GridPane.setHgrow(cards.get(column), Priority.ALWAYS);
                GridPane.setVgrow(cards.get(column), Priority.ALWAYS);
                grid.add(cards.get(column), column, 0);
            }
            for (int row = 1; row < cards.size() - 1; row++) {
                Node card = cards.get(row + 1);
                GridPane.setHgrow(card, Priority.ALWAYS);
                GridPane.setVgrow(card, Priority.ALWAYS);
                grid.add(card, 0, row, 2, 1);
            }
        }

        int rows = columns == 1 ? cards.size() : cards.size() - 1;
        for (int row = 0; row < rows; row++) {
            RowConstraints constraint = new RowConstraints();
            constraint.setVgrow(Priority.ALWAYS);
            constraint.setMinHeight(row == 0 ? 260 : 320);
            grid.getRowConstraints().add(constraint);
        }
    }

    private static void cardChildren(VBox card, javafx.scene.Node... nodes) {
        card.getChildren().addAll(nodes);
    }

    private String fmt(BigDecimal value) { return app.formatMoney(value); }

    private static final class OverviewData {
        final List<JournalEntry> entries;
        final List<Account> accounts;
        final List<RecentView> recent;
        OverviewData(List<JournalEntry> entries, List<Account> accounts, List<RecentView> recent) {
            this.entries=entries; this.accounts=accounts; this.recent=recent;
        }
    }

}
