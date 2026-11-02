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

public final class LedgerSection extends AccountingSection {
    private final ComboBox<Account> accounts = new ComboBox<>();
    private final TableView<AccountingRepository.LedgerLine> table = new TableView<>();
    private final Label totals = new Label("Debit total: 0    Credit total: 0");
    private final TextField search = new TextField();
    private List<AccountingRepository.LedgerLine> allRows = new ArrayList<>();
    private Long requestedAccountId;

    public LedgerSection(MainApp app, User user) {
        super(app, user, "Ledger");
        Button refresh = new Button("Reload");
        refresh.setOnAction(e -> loadAccounts());
        search.setPromptText("Filter explanations");
        Button filter = new Button("Search");
        filter.setOnAction(e -> applyFilter());
        accounts.setPromptText("Select an account");
        accounts.setOnAction(e -> loadLedger());
        HBox controls = new HBox(10, new Label("Account"), accounts, search, filter, refresh);
        controls.setPadding(new Insets(18, 0, 12, 0));
        table.getColumns().add(col("Date", x -> x.date.toString(), 115));
        table.getColumns().add(col("Account name", x -> x.account, 170));
        table.getColumns().add(col("Explanation", x -> x.explanation, 300));
        TableColumn<AccountingRepository.LedgerLine, String> currencyColumn =
                col("Currency", x -> app.currencySymbol(), 55);
        Label currencyHeading = new Label("Currency");
        currencyHeading.setTooltip(new Tooltip(
                "Display preference only. Recorded currency codes remain unchanged; no conversion is performed."));
        currencyColumn.setText(null);
        currencyColumn.setGraphic(currencyHeading);
        currencyColumn.setCellFactory(column -> new TableCell<AccountingRepository.LedgerLine, String>() {
            @Override
            protected void updateItem(String symbol, boolean empty) {
                super.updateItem(symbol, empty);
                if (empty) {
                    setText(null);
                    setTooltip(null);
                    return;
                }

                setText(symbol);
                AccountingRepository.LedgerLine line = getTableRow() == null
                        ? null : getTableRow().getItem();
                if (line == null) {
                    setTooltip(null);
                } else {
                    setTooltip(new Tooltip("Saved currency code: " + line.currency
                            + ". Display preference only; no conversion."));
                }
            }
        });
        table.getColumns().add(currencyColumn);
        table.getColumns().add(col("Debit", x -> fmt(x.debit), 120));
        table.getColumns().add(col("Credit", x -> fmt(x.credit), 120));
        table.getColumns().add(col("Running balance", x -> fmt(x.balance), 150));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Select an account to see its transactions."));
        totals.setPadding(new Insets(12, 0, 0, 0));
        Label explanation = new Label("Running balance adds debits for assets and expenses; it adds credits for liabilities, equity, and revenue. Totals remain grouped by saved currency code. The displayed symbol is a preference only; amounts are not converted.");
        explanation.setWrapText(true);
        EntriTheme.muted(explanation);
        VBox content = new VBox(8, controls, table, totals, explanation);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    private TableColumn<AccountingRepository.LedgerLine, String> col(
            String title, java.util.function.Function<AccountingRepository.LedgerLine, String> value, double width) {
        TableColumn<AccountingRepository.LedgerLine, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new ReadOnlyStringWrapper(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private String fmt(BigDecimal value) { return app.formatMoney(value); }

    private void loadAccounts() {
        app.async(() -> app.accounting().accounts(user.getId()), list -> {
            Account previous = accounts.getValue();
            accounts.setItems(FXCollections.observableArrayList(list));
            if (requestedAccountId != null) {
                long requested = requestedAccountId;
                requestedAccountId = null;
                Account requestedAccount = findAccount(list, requested);
                if (requestedAccount == null) {
                    table.getItems().clear();
                    app.showError("Ledger account unavailable",
                            "This account was deleted or is not available to your account.");
                    return;
                }
                accounts.getSelectionModel().select(requestedAccount);
            } else if (previous != null) {
                accounts.getSelectionModel().select(findAccount(list, previous.getId()));
            }
            if (accounts.getValue() == null && !list.isEmpty()) accounts.getSelectionModel().selectFirst();
        }, ex -> app.showError("Could not load accounts", MainApp.safeMessage(ex)));
    }

    private Account findAccount(List<Account> list, long accountId) {
        for (Account account : list) {
            if (account.getId() == accountId) {
                return account;
            }
        }
        return null;
    }

    private void loadLedger() {
        Account account = accounts.getValue();
        if (account == null) { table.getItems().clear(); return; }
        totals.setText("Loading ledger transactions...");
        app.async(() -> app.accounting().ledger(user.getId(), account.getId()), rows -> {
            allRows = rows;
            applyFilter();
            app.async(() -> {
                app.accounting().recordRecent(user.getId(), "LEDGER_ACCOUNT", account.getId(), account.getName());
                return null;
            }, ignored -> {}, ex -> app.showError("Could not save recent view", MainApp.safeMessage(ex)));
        }, ex -> app.showError("Could not load ledger", MainApp.safeMessage(ex)));
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
            List<AccountingRepository.LedgerLine> visible = new ArrayList<>();
            for (AccountingRepository.LedgerLine row : allRows) {
                if (row.explanation.toLowerCase(java.util.Locale.ROOT).contains(query)) {
                    visible.add(row);
                }
            }
            table.setItems(FXCollections.observableArrayList(visible));
            Map<String, BigDecimal[]> totalsByCurrency = new TreeMap<>();
            for (AccountingRepository.LedgerLine row : visible) {
                BigDecimal[] total = totalsByCurrency.get(row.currency);
                if (total == null) {
                    total = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                    totalsByCurrency.put(row.currency, total);
                }
                total[0] = total[0].add(row.debit);
                total[1] = total[1].add(row.credit);
            }

            List<String> summaries = new ArrayList<>();
            for (Map.Entry<String, BigDecimal[]> item : totalsByCurrency.entrySet()) {
                BigDecimal[] total = item.getValue();
                summaries.add(item.getKey() + " — Debit: " + fmt(total[0])
                        + "    Credit: " + fmt(total[1]));
            }
            if (summaries.isEmpty()) {
                totals.setText("No transactions for this account.");
                return;
            }

            Map<String, BigDecimal> endingByCurrency = new TreeMap<>();
            for (AccountingRepository.LedgerLine row : allRows) {
                endingByCurrency.put(row.currency, row.balance);
            }
            List<String> ending = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> item : endingByCurrency.entrySet()) {
                ending.add(item.getKey() + " ending balance: " + fmt(item.getValue()));
            }
            totals.setText("Filtered debit / credit totals: " + String.join("       ", summaries)
                    + "       " + String.join("       ", ending));
    }

    @Override public void reload() { loadAccounts(); }

    public void openAccount(long accountId) {
        requestedAccountId = accountId;
        loadAccounts();
    }
}
