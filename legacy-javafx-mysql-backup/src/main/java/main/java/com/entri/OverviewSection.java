package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
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

    public OverviewSection(MainApp app, User user, Consumer<String> navigate,
                           Consumer<JournalEntry> openEntry, Runnable addEntry) {
        super(app, user, "Dashboard");
        this.navigate = navigate;
        this.openEntry = openEntry;
        this.addEntry = addEntry;
        greeting.setText("Welcome, " + user.getUsername());
        greeting.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + EntriTheme.TEXT + ";");
        EntriTheme.muted(recordSummary);
        VBox banner = new VBox(6, greeting, new Label("Manage your financial records in one place."), recordSummary);
        banner.setPadding(new Insets(8, 0, 6, 0));
        cards.setPadding(new Insets(4, 0, 12, 0));
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
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
            GridPane summary = new GridPane();
            summary.setHgap(14);
            summary.setVgap(14);
            summary.add(summaryCard("Journal entries", String.valueOf(data.entries.size()), "View Journal Entry", "journal"), 0, 0);
            summary.add(summaryCard("Ledger accounts", String.valueOf(data.accounts.size()), "Open General Ledger", "ledger"), 1, 0);
            summary.add(summaryCard("Recent views", String.valueOf(data.recent.size()), "View Recent Views", "recent"), 2, 0);
            ColumnConstraints summaryColumn = new ColumnConstraints();
            summaryColumn.setPercentWidth(33.33);
            summary.getColumnConstraints().addAll(summaryColumn, summaryColumn, summaryColumn);
            cards.getChildren().addAll(summary, createJournalPreview(data.entries),
                    createLedgerPreview(data.accounts), createWorksheetPreview(data.entries),
                    createRecentPreview(data.recent));
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
        preview.setPrefHeight(190);
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
        preview.setPrefHeight(190);
        preview.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
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
        TableView<WorksheetPreviewRow> preview = new TableView<>();
        preview.setPrefHeight(155);
        preview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<WorksheetPreviewRow, String> account = new TableColumn<>("Account");
        account.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name));
        TableColumn<WorksheetPreviewRow, String> debit = new TableColumn<>("Trial Balance Debit");
        debit.setCellValueFactory(d -> new ReadOnlyStringWrapper(fmt(d.getValue().debit)));
        TableColumn<WorksheetPreviewRow, String> credit = new TableColumn<>("Trial Balance Credit");
        credit.setCellValueFactory(d -> new ReadOnlyStringWrapper(fmt(d.getValue().credit)));
        preview.getColumns().add(account);
        preview.getColumns().add(debit);
        preview.getColumns().add(credit);
        preview.setPlaceholder(new Label(entries.isEmpty()
                ? "No journal records to include in a worksheet yet." : "Loading worksheet preview..."));
        card.getChildren().addAll(sectionHeader("12-Column Worksheet", open), text, preview);
        app.async(() -> app.accounting().worksheet(user.getId()), data -> {
            java.util.Map<String, WorksheetPreviewRow> grouped = new java.util.LinkedHashMap<>();
            for (AccountingRepository.WorksheetRow row : data) {
                String currencyCode = row.currency == null ? "—" : row.currency;
                String key = row.id + ":" + currencyCode;
                WorksheetPreviewRow value = grouped.get(key);
                if (value == null) {
                    value = new WorksheetPreviewRow(row.name + " (" + currencyCode + ")");
                    grouped.put(key, value);
                }
                if (row.entryType != null && !"ADJUSTMENT".equals(row.entryType)
                        && row.debit != null && row.credit != null) {
                    value.debit = value.debit.add(row.debit);
                    value.credit = value.credit.add(row.credit);
                }
            }
            preview.setItems(FXCollections.observableArrayList(firstWorksheetRows(grouped, 5)));
        }, ex -> preview.setPlaceholder(new Label(MainApp.safeMessage(ex))));
        return card;
    }

    private VBox createRecentPreview(List<RecentView> recent) {
        VBox card = cardBox();
        Button all = new Button("View All");
        Button refresh = new Button("Refresh");
        EntriTheme.secondary(all);
        EntriTheme.secondary(refresh);
        all.setOnAction(e -> navigate.accept("recent"));
        refresh.setOnAction(e -> reload());
        VBox rows = new VBox(7);
        if (recent.isEmpty()) {
            Label empty = new Label("You have not opened any records yet.");
            EntriTheme.muted(empty);
            rows.getChildren().add(empty);
        } else {
            int count = 0;
            for (RecentView view : recent) {
                if (count >= 5) break;
                Button item = new Button(view.getName() + "   ·   " + RecentViewsSection.readableType(view.getType()));
                item.setMaxWidth(Double.MAX_VALUE);
                item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                EntriTheme.secondary(item);
                item.setOnAction(e -> app.openRecent(view));
                rows.getChildren().add(item);
                count++;
            }
        }
        card.getChildren().addAll(sectionHeader("Recent Views", new HBox(8, all, refresh)), rows);
        return card;
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

    private List<WorksheetPreviewRow> firstWorksheetRows(
            java.util.Map<String, WorksheetPreviewRow> rows, int limit) {
        List<WorksheetPreviewRow> result = new ArrayList<>();
        for (WorksheetPreviewRow row : rows.values()) {
            if (result.size() >= limit) break;
            result.add(row);
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
        box.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: "
                + EntriTheme.BORDER + "; -fx-border-radius: 12;");
        return box;
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

    private static final class WorksheetPreviewRow {
        final String name;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        WorksheetPreviewRow(String name) { this.name=name; }
    }
}
