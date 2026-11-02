package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class JournalEntrySection extends AccountingSection {
    private final TableView<JournalEntry> table = new TableView<>();
    private final TextField search = new TextField();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final ComboBox<AccountFilterOption> accountFilter = new ComboBox<>();
    private final Label transactionCount = new Label("Displayed transactions: 0");
    private final Label totals = new Label();
    private final FlowPane balanceStatus = new FlowPane(10, 4);
    private final Label operationStatus = new Label();
    private boolean accountFilterUpdating;
    private boolean accountsLoading;
    private boolean accountsLoaded;
    private boolean generalJournalMode;
    private long entriesRequest;
    private Map<String, JournalTotalsByCurrency.CurrencyTotals> displayedTotals = Map.of();

    public JournalEntrySection(MainApp app, User user) {
        super(app, user, "Journal Entry",
                "Record, review, and edit balanced transactions. Debit and credit amounts are shown by saved currency.");
        TableColumn<JournalEntry, String> date = column("Date", e -> e.getDate().toString());
        TableColumn<JournalEntry, String> particulars = column("Particulars", JournalEntry::getParticulars);
        TableColumn<JournalEntry, String> currency = column("Currency", JournalEntry::getCurrency);
        TableColumn<JournalEntry, String> debit = column("Debit",
                e -> formatEntryAmount(e.getDebitTotal(), e.getCurrency()));
        TableColumn<JournalEntry, String> credit = column("Credit",
                e -> formatEntryAmount(e.getCreditTotal(), e.getCurrency()));
        TableColumn<JournalEntry, String> kind = column("Type", JournalEntry::getType);
        table.getColumns().add(date);
        table.getColumns().add(particulars);
        table.getColumns().add(currency);
        table.getColumns().add(debit);
        table.getColumns().add(credit);
        table.getColumns().add(kind);
        EntriTheme.table(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transactions match the current filters."));
        EntriTheme.muted(transactionCount);
        totals.setWrapText(true);
        operationStatus.setWrapText(true);
        table.setRowFactory(view -> {
            TableRow<JournalEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) viewEntry(row.getItem());
            });
            return row;
        });

        search.setPromptText("Search ID, particulars, account");
        Button find = new Button("Search");
        Button refresh = new Button("Refresh");
        Button export = new Button("Export CSV");
        Button add = new Button("Add New Journal Entry");
        Button view = new Button("View");
        Button edit = new Button("Edit");
        Button delete = new Button("Delete");
        EntriTheme.primary(add);
        EntriTheme.secondary(delete);
        EntriTheme.secondary(export);
        find.setOnAction(e -> reloadEntries());
        refresh.setOnAction(e -> reload());
        export.setOnAction(e -> exportEntries());
        Button clearFilters = new Button("Clear filters");
        clearFilters.setOnAction(event -> {
            search.clear();
            fromDate.setValue(null);
            toDate.setValue(null);
            selectAllAccounts();
            reloadEntries();
        });
        accountFilter.setConverter(new StringConverter<>() {
            @Override public String toString(AccountFilterOption option) {
                return option == null ? "All Accounts" : option.label();
            }
            @Override public AccountFilterOption fromString(String value) { return null; }
        });
        accountFilter.getItems().add(new AccountFilterOption(null));
        accountFilter.getSelectionModel().selectFirst();
        accountFilter.setPromptText("All Accounts");
        accountFilter.setPrefWidth(220);
        accountFilter.setOnAction(event -> {
            if (!accountFilterUpdating) reloadEntries();
        });
        add.setOnAction(e -> openEditor(null, false));
        view.setOnAction(e -> selectedAction(this::viewEntry, "Select a journal entry to view."));
        edit.setOnAction(e -> selectedAction(entry -> openEditor(entry, false), "Select a journal entry to edit."));
        delete.setOnAction(e -> deleteSelected());
        view.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        FlowPane actions = new FlowPane(8, 8, add, view, edit, delete, refresh, export);
        FlowPane filters = new FlowPane(8, 8, new Label("Account"), accountFilter,
                new Label("From"), fromDate, new Label("To"), toDate, search, find, clearFilters);
        EntriTheme.secondary(clearFilters);
        delete.setTooltip(new Tooltip("Delete the selected entry and its linked ledger posting lines."));
        fromDate.setPromptText("Start date");
        toDate.setPromptText("End date");
        search.setPrefWidth(190);
        search.setMinWidth(150);
        search.setMaxWidth(280);
        fromDate.setPrefWidth(145);
        toDate.setPrefWidth(145);
        search.setPromptText("Search ID, particulars, account");
        search.setOnAction(event -> reloadEntries());
        filters.setPadding(new Insets(0, 0, 8, 0));
        Label hint = new Label("Balanced entries support multiple debit and credit lines. Double-click a row to view it.");
        EntriTheme.muted(hint);
        balanceStatus.setVisible(false);
        balanceStatus.setManaged(false);
        VBox content = new VBox(8, actions, filters, table, transactionCount, totals,
                balanceStatus, operationStatus, hint);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    public void addAdjustment() { openEditor(null, true); }
    public void addNewEntry() { openEditor(null, false); }

    public void setGeneralJournalMode(boolean enabled) {
        if (generalJournalMode != enabled) {
            displayedTotals = Map.of();
            balanceStatus.getChildren().clear();
        }
        generalJournalMode = enabled;
        balanceStatus.setVisible(enabled);
        balanceStatus.setManaged(enabled);
        updateBalanceStatus();
    }

    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    public void openRecent(long entryId) {
        app.async(() -> app.accounting().entry(user.getId(), entryId), entry -> {
            if (entry == null) {
                app.showError("Journal entry unavailable", "This entry was deleted or is not available to your account.");
                reload();
                return;
            }
            viewEntry(entry);
        },
                ex -> app.showError("Could not load journal entry", MainApp.safeMessage(ex)));
    }

    private void selectedAction(Consumer<JournalEntry> action, String emptyMessage) {
        JournalEntry selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) app.showError("Journal entry", emptyMessage);
        else action.accept(selected);
    }

    private TableColumn<JournalEntry, String> column(String title, java.util.function.Function<JournalEntry, String> value) {
        TableColumn<JournalEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        if ("Debit".equals(title) || "Credit".equals(title)) {
            column.setComparator((first, second) -> compareAmounts(first, second));
        }
        if ("Debit".equals(title) || "Credit".equals(title)) {
            column.setCellFactory(ignored -> new TableCell<JournalEntry, String>() {
                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                }
            });
        }
        if ("Particulars".equals(title)) {
            column.setPrefWidth(340);
        } else if ("Currency".equals(title)) {
            column.setPrefWidth(85);
        } else {
            column.setPrefWidth(130);
        }
        column.setMinWidth("Particulars".equals(title) ? 160
                : "Currency".equals(title) ? 75
                : "Type".equals(title) ? 75 : 95);
        return column;
    }

    private static int compareAmounts(String first, String second) {
        try {
            return new BigDecimal(first.replaceAll("[^0-9.\\-]", ""))
                    .compareTo(new BigDecimal(second.replaceAll("[^0-9.\\-]", "")));
        } catch (NumberFormatException ex) {
            return first.compareTo(second);
        }
    }

    static String money(BigDecimal amount) { return amount.stripTrailingZeros().toPlainString(); }

    private static String formatEntryAmount(BigDecimal amount, String currencyCode) {
        return currencyCode + " " + CurrencyFormatter.format(amount, currencyCode);
    }

    private void exportEntries() {
        List<List<?>> rows = new ArrayList<>();
        for (JournalEntry entry : table.getItems()) {
            rows.add(List.of(entry.getDate(), entry.getParticulars(), entry.getCurrency(),
                    entry.getDebitTotal(), entry.getCreditTotal(), entry.getType()));
        }
        exportCsv("journal_entries.csv",
                List.of("Date", "Particulars", "Currency", "Debit Total", "Credit Total", "Type"),
                rows);
    }

    @Override public void reload() {
        if (accountsLoading) return;
        entriesRequest++;
        displayedTotals = Map.of();
        balanceStatus.getChildren().clear();
        accountsLoading = true;
        accountFilter.setDisable(true);
        operationStatus.setStyle("");
        operationStatus.setText("Loading accounts...");
        Long selectedAccountId = selectedAccountId();
        app.async(() -> app.accounting().accounts(user.getId()), accounts -> {
            accountsLoading = false;
            accountsLoaded = true;
            accountFilter.setDisable(false);
            updateAccountFilter(accounts, selectedAccountId);
            reloadEntries();
        }, ex -> {
            accountsLoading = false;
            accountsLoaded = false;
            accountFilter.setDisable(false);
            markResultsUnavailable("Results unavailable because user accounts could not be loaded.");
            app.showError("Could not load accounts", MainApp.safeMessage(ex));
        });
    }

    private void reloadEntries() {
        if (accountsLoading) return;
        if (!accountsLoaded) {
            reload();
            return;
        }
        String filter = search.getText();
        LocalDate start;
        LocalDate end;
        try {
            start = readFilterDate(fromDate, "start");
            end = readFilterDate(toDate, "end");
        } catch (IllegalArgumentException ex) {
            markResultsUnavailable(ex.getMessage());
            app.showError("Invalid date", ex.getMessage());
            return;
        }
        if (start != null && end != null && start.isAfter(end)) {
            String message = "The start date must be on or before the end date.";
            markResultsUnavailable(message);
            app.showError("Invalid date range", message);
            return;
        }
        AccountFilterOption selected = accountFilter.getValue();
        Long accountId = selected == null || selected.account == null
                ? null : selected.account.getId();
        long request = ++entriesRequest;
        displayedTotals = Map.of();
        balanceStatus.getChildren().clear();
        table.setPlaceholder(new Label("Loading transactions..."));
        totals.setText("Loading journal entries...");
        operationStatus.setStyle("");
        operationStatus.setText("Loading transactions...");
        app.async(() -> app.accounting().entries(user.getId(), filter, start, end, accountId), entries -> {
            if (request != entriesRequest) return;
            table.setItems(FXCollections.observableArrayList(entries));
            transactionCount.setText("Displayed transactions: " + entries.size());
            displayedTotals = JournalTotalsByCurrency.calculate(entries);
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, JournalTotalsByCurrency.CurrencyTotals> item
                    : displayedTotals.entrySet()) {
                JournalTotalsByCurrency.CurrencyTotals amount = item.getValue();
                values.add(item.getKey() + " — Debit: " + formatEntryAmount(amount.debit(), item.getKey())
                        + " / Credit: " + formatEntryAmount(amount.credit(), item.getKey()));
            }
            totals.setText("Totals by saved currency: "
                    + (values.isEmpty() ? "none" : String.join("    ", values)));
            updateBalanceStatus();
            table.setPlaceholder(new Label("No transactions match the current filters."));
            operationStatus.setText(entries.isEmpty()
                    ? "No transactions match the current filters." : "");
        }, ex -> {
            if (request != entriesRequest) return;
            markResultsUnavailable("Results unavailable because transactions could not be loaded.");
            app.showError("Could not load journal entries", MainApp.safeMessage(ex));
        });
    }

    private void updateAccountFilter(List<Account> accounts, Long selectedAccountId) {
        accountFilterUpdating = true;
        try {
            accountFilter.getItems().clear();
            AccountFilterOption allAccounts = new AccountFilterOption(null);
            accountFilter.getItems().add(allAccounts);
            AccountFilterOption selected = allAccounts;
            for (Account account : accounts) {
                AccountFilterOption option = new AccountFilterOption(account);
                accountFilter.getItems().add(option);
                if (selectedAccountId != null && account.getId() == selectedAccountId) {
                    selected = option;
                }
            }
            accountFilter.setValue(selected);
        } finally {
            accountFilterUpdating = false;
        }
    }

    private void selectAllAccounts() {
        accountFilterUpdating = true;
        try {
            if (!accountFilter.getItems().isEmpty()) {
                accountFilter.getSelectionModel().selectFirst();
            }
        } finally {
            accountFilterUpdating = false;
        }
    }

    private Long selectedAccountId() {
        AccountFilterOption selected = accountFilter.getValue();
        return selected == null || selected.account == null ? null : selected.account.getId();
    }

    private static LocalDate readFilterDate(DatePicker picker, String field) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) {
            picker.setValue(null);
            return null;
        }
        try {
            LocalDate value = picker.getConverter().fromString(text);
            if (value == null) throw new DateTimeParseException("No date was entered.", text, 0);
            picker.setValue(value);
            return value;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Enter a valid " + field + " date.", ex);
        }
    }

    private void markResultsUnavailable(String message) {
        entriesRequest++;
        displayedTotals = Map.of();
        balanceStatus.getChildren().clear();
        table.setItems(FXCollections.observableArrayList());
        table.setPlaceholder(new Label("Results unavailable."));
        transactionCount.setText("Displayed transactions: 0");
        totals.setText("");
        operationStatus.setStyle("-fx-text-fill: " + EntriTheme.ERROR + ";");
        operationStatus.setText("Results unavailable: " + message);
    }

    private void updateBalanceStatus() {
        balanceStatus.getChildren().clear();
        if (!generalJournalMode) return;
        for (Map.Entry<String, JournalTotalsByCurrency.CurrencyTotals> item
                : displayedTotals.entrySet()) {
            boolean balanced = item.getValue().isBalanced();
            Label status = new Label(item.getKey() + (balanced
                    ? " — Debits and credits are equal"
                    : " — Warning: debits and credits differ"));
            status.setStyle("-fx-text-fill: "
                    + (balanced ? EntriTheme.SECONDARY : EntriTheme.ERROR) + ";");
            balanceStatus.getChildren().add(status);
        }
    }

    private static final class AccountFilterOption {
        private final Account account;

        private AccountFilterOption(Account account) {
            this.account = account;
        }

        private String label() {
            return account == null ? "All Accounts" : account.toString();
        }
    }

    private void openEditor(JournalEntry existing, boolean adjustment) {
        app.async(() -> app.accounting().accounts(user.getId()), accounts -> {
            if (accounts.isEmpty()) {
                app.showError("No ledger accounts", "Your chart of accounts is empty. Contact your administrator before posting entries.");
                return;
            }
            showEntryDialog(existing, accounts, adjustment);
        }, ex -> app.showError("Could not load accounts", MainApp.safeMessage(ex)));
    }

    private void showEntryDialog(JournalEntry existing, List<Account> accounts, boolean adjustment) {
        Dialog<JournalDraft> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(existing == null ? (adjustment ? "Add Adjustment" : "Add Journal Entry") : "Edit Journal Entry");
        ButtonType saveType = new ButtonType("Save Entry", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        DatePicker date = new DatePicker(existing == null ? LocalDate.now() : existing.getDate());
        TextField particulars = new TextField(existing == null ? "" : existing.getParticulars());
        List<String> currencyOptions = new ArrayList<>();
        currencyOptions.add("PHP");
        currencyOptions.add("USD");
        if (existing != null && !currencyOptions.contains(existing.getCurrency())) {
            currencyOptions.add(existing.getCurrency());
        }
        ComboBox<String> currency = new ComboBox<>(FXCollections.observableArrayList(currencyOptions));
        currency.setValue(existing == null ? app.currencyCode() : existing.getCurrency());
        ComboBox<String> entryType = new ComboBox<>(FXCollections.observableArrayList("STANDARD", "ADJUSTMENT"));
        entryType.setValue(adjustment ? "ADJUSTMENT" : existing == null ? "STANDARD" : existing.getType());
        entryType.setDisable(adjustment);

        ObservableList<LineEditor> lines = FXCollections.observableArrayList();
        VBox lineBox = new VBox(8);
        Label totalsLabel = new Label();
        Label balanceStatus = new Label();
        boolean[] saving = {false};
        Runnable updateStatus = () -> updateBalance(lines, accounts.size(), dateValue(date),
                particulars.getText(), currency.getValue(), totalsLabel, balanceStatus,
                saveButton, saving[0]);
        Runnable updateTotals = updateStatus;
        currency.valueProperty().addListener((observable, previous, current) -> updateTotals.run());
        date.valueProperty().addListener((observable, previous, current) -> updateStatus.run());
        date.getEditor().textProperty().addListener((observable, previous, current) -> updateStatus.run());
        particulars.textProperty().addListener((observable, previous, current) -> updateStatus.run());
        Runnable renderLines = () -> {
            lineBox.getChildren().clear();
            for (LineEditor line : lines) {
                line.removeButton.setDisable(lines.size() <= 2);
                lineBox.getChildren().add(line.row);
            }
            updateTotals.run();
        };
        Button addLine = new Button("Add Line");
        EntriTheme.secondary(addLine);
        addLine.setOnAction(e -> {
            LineEditor line = createLine(accounts, null, lines, updateTotals, renderLines);
            lines.add(line);
            renderLines.run();
        });
        if (existing != null) {
            for (JournalLine line : existing.getLines()) {
                lines.add(createLine(accounts, line, lines, updateTotals, renderLines));
            }
        } else {
            lines.add(createLine(accounts, null, lines, updateTotals, renderLines));
            lines.add(createLine(accounts, null, lines, updateTotals, renderLines));
        }
        renderLines.run();

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.add(new Label("Entry date"), 0, 0);
        fields.add(date, 1, 0);
        fields.add(new Label("Particulars"), 0, 1);
        fields.add(particulars, 1, 1);
        fields.add(new Label("Currency"), 0, 2);
        fields.add(currency, 1, 2);
        fields.add(new Label("Type"), 0, 3);
        fields.add(entryType, 1, 3);
        HBox lineHeading = new HBox(10,
                sized(new Label("Account"), 230), sized(new Label("Debit"), 120),
                sized(new Label("Credit"), 120), sized(new Label(""), 80));
        Label section = new Label("Journal lines");
        section.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        VBox editorContent = new VBox(12, fields, section, lineHeading, lineBox, addLine, totalsLabel, balanceStatus);
        editorContent.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(editorContent);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(470);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setMaxWidth(760);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);
        progress.setManaged(false);
        Label savingLabel = new Label("Saving transaction...");
        savingLabel.setVisible(false);
        savingLabel.setManaged(false);
        HBox savingIndicator = new HBox(8, progress, savingLabel);
        savingIndicator.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        editorContent.getChildren().add(savingIndicator);
        updateStatus.run();
        dialog.setOnCloseRequest(event -> {
            if (saving[0]) event.consume();
        });
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (saving[0]) return;
            JournalDraft draft;
            try {
                LocalDate transactionDate = dateValue(date);
                if (transactionDate == null) {
                    throw new IllegalArgumentException("Enter a valid transaction date.");
                }
                if (particulars.getText() == null || particulars.getText().trim().isEmpty()) {
                    throw new IllegalArgumentException("Enter particulars for this entry.");
                }
                if (particulars.getText().trim().length() > 500) {
                    throw new IllegalArgumentException("Particulars must be no longer than 500 characters.");
                }
                if (currency.getValue() == null
                        || !CurrencyFormatter.isSupported(currency.getValue())) {
                    throw new IllegalArgumentException("Choose a supported currency.");
                }
                if (accounts.size() < 2) {
                    throw new IllegalArgumentException("At least two accounts are required to post a transaction.");
                }
                draft = new JournalDraft(transactionDate, particulars.getText().trim(),
                        currency.getValue(), entryType.getValue(), collectLines(lines));
            } catch (IllegalArgumentException ex) {
                app.showError("Entry is not ready to save", ex.getMessage());
                return;
            }
            String summary = "Date: " + draft.date
                    + "\nParticulars: " + draft.particulars
                    + "\nTotal debit: " + CurrencyFormatter.format(total(draft.lines, true), draft.currency)
                    + "\nTotal credit: " + CurrencyFormatter.format(total(draft.lines, false), draft.currency)
                    + "\n\nDo you want to proceed with posting this transaction?";
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, summary,
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.initOwner(dialog.getOwner());
            confirmation.setTitle("Confirm transaction");
            confirmation.setHeaderText("Post this transaction?");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            saving[0] = true;
            editorContent.setDisable(true);
            cancelButton.setDisable(true);
            progress.setVisible(true);
            progress.setManaged(true);
            savingLabel.setVisible(true);
            savingLabel.setManaged(true);
            updateStatus.run();
            app.async(() -> app.accounting().save(user.getId(),
                    existing == null ? 0 : existing.getId(), draft.date, draft.particulars,
                    draft.currency, draft.type, draft.lines), id -> {
                saving[0] = false;
                operationStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
                operationStatus.setText(existing == null ? "Journal entry saved." : "Journal entry updated.");
                dialog.close();
                reload();
            }, ex -> {
                saving[0] = false;
                editorContent.setDisable(false);
                cancelButton.setDisable(false);
                progress.setVisible(false);
                progress.setManaged(false);
                savingLabel.setVisible(false);
                savingLabel.setManaged(false);
                updateStatus.run();
                app.showError("Could not save entry", MainApp.safeMessage(ex));
            });
        });
        dialog.show();
    }

    private static LineEditor createLine(List<Account> accounts, JournalLine existing,
                                         ObservableList<LineEditor> lines, Runnable changed, Runnable render) {
        LineEditor[] holder = new LineEditor[1];
        Runnable remove = () -> {
            if (lines.size() > 2) {
                lines.remove(holder[0]);
                render.run();
            }
        };
        holder[0] = new LineEditor(accounts, existing, changed, remove);
        return holder[0];
    }

    private static Region sized(javafx.scene.Node node, double width) {
        StackPane pane = new StackPane(node);
        pane.setMinWidth(Math.min(width, 90));
        pane.setPrefWidth(width);
        pane.setMaxWidth(Double.MAX_VALUE);
        return pane;
    }

    private static void updateBalance(List<LineEditor> lines, int accountCount, LocalDate date,
                                      String particulars, String currencyCode, Label totalLabel,
                                      Label status, Button saveButton, boolean saving) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        boolean invalidAmount = false;
        boolean incompleteLine = false;
        List<JournalLine> completeLines = new ArrayList<>();
        for (LineEditor line : lines) {
            debit = debit.add(line.value(line.debit));
            credit = credit.add(line.value(line.credit));
            if (!line.isInputValid()) {
                invalidAmount = true;
            } else if (!line.isComplete()) {
                incompleteLine = true;
            } else {
                completeLines.add(new JournalLine(line.account.getValue(),
                        line.value(line.debit), line.value(line.credit)));
            }
        }
        totalLabel.setText("Debit total: " + CurrencyFormatter.format(debit, currencyCode)
                + "     Credit total: " + CurrencyFormatter.format(credit, currencyCode)
                + "     Difference: " + CurrencyFormatter.format(debit.subtract(credit).abs(), currencyCode));
        String message;
        if (accountCount < 2) {
            message = "Incomplete — at least two accounts are required to post.";
        } else if (date == null) {
            message = "Incomplete — choose a transaction date.";
        } else if (particulars == null || particulars.trim().isEmpty()) {
            message = "Incomplete — enter transaction particulars.";
        } else if (particulars.trim().length() > 500) {
            message = "Invalid — particulars must be no longer than 500 characters.";
        } else if (!CurrencyFormatter.isSupported(currencyCode)) {
            message = "Incomplete — choose a supported currency.";
        } else if (lines.size() < 2) {
            message = "Incomplete — at least two journal lines are required.";
        } else if (invalidAmount) {
            message = "Invalid amount — enter non-negative amounts with up to two decimal places.";
        } else if (incompleteLine) {
            message = "Incomplete — select an account and enter a debit or credit for each line.";
        } else if (AccountingService.hasSameAccountForSimpleTransaction(completeLines)) {
            message = "Invalid — use different accounts for the debit and credit sides.";
        } else if (debit.signum() <= 0 || debit.compareTo(credit) != 0) {
            message = "Unbalanced — debit and credit totals must match and be positive.";
        } else {
            message = "Balanced — ready to post.";
        }
        boolean ready = message.startsWith("Balanced");
        status.setText(saving ? "Saving transaction..." : message);
        status.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                + (ready ? EntriTheme.SUCCESS : EntriTheme.ERROR) + ";");
        saveButton.setDisable(saving || !ready);
    }

    private static BigDecimal total(List<JournalLine> lines, boolean debit) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JournalLine line : lines) sum = sum.add(debit ? line.getDebit() : line.getCredit());
        return sum;
    }

    private static LocalDate dateValue(DatePicker date) {
        String text = date.getEditor().getText();
        if (text == null || text.isBlank()) return null;
        try {
            return date.getConverter().fromString(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static List<JournalLine> collectLines(List<LineEditor> editors) {
        List<JournalLine> lines = new ArrayList<>();
        for (LineEditor editor : editors) {
            Account account = editor.account.getValue();
            if (account == null) throw new IllegalArgumentException("Select an account for every line.");
            BigDecimal debit = parseAmount(editor.debit.getText());
            BigDecimal credit = parseAmount(editor.credit.getText());
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new IllegalArgumentException("Every line must contain a debit or a credit amount.");
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new IllegalArgumentException("A line cannot contain both debit and credit amounts.");
            }
            lines.add(new JournalLine(account, debit, credit));
        }
        if (lines.size() < 2) throw new IllegalArgumentException("At least two journal lines are required.");
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JournalLine line : lines) {
            debit = debit.add(line.getDebit());
            credit = credit.add(line.getCredit());
        }
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0) {
            throw new IllegalArgumentException("Entry is unbalanced. Debit and credit totals must match and be positive.");
        }
        AccountingService.validateSimpleTransactionAccounts(lines);
        return Collections.unmodifiableList(lines);
    }

    private static BigDecimal parseAmount(String input) {
        if (input == null || input.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal amount = new BigDecimal(input.trim());
            if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() > 19
                    || amount.compareTo(new BigDecimal("1000000000000000")) >= 0) {
                throw new IllegalArgumentException("Amounts must be non-negative with up to two decimal places.");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter valid decimal amounts, for example 125.50.");
        }
    }

    private static final class LineEditor {
        final ComboBox<Account> account = new ComboBox<>();
        final TextField debit = new TextField();
        final TextField credit = new TextField();
        final Button removeButton = new Button("Remove");
        final HBox row;

        LineEditor(List<Account> accounts, JournalLine existing, Runnable changed, Runnable removeAction) {
            account.setItems(FXCollections.observableArrayList(accounts));
            account.setConverter(new StringConverter<>() {
                @Override public String toString(Account value) { return value == null ? "" : value.toString(); }
                @Override public Account fromString(String value) { return null; }
            });
            account.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(account, Priority.ALWAYS);
            debit.setPromptText("0.00");
            credit.setPromptText("0.00");
            debit.setPrefWidth(110);
            debit.setMinWidth(75);
            debit.setMaxWidth(180);
            credit.setPrefWidth(110);
            credit.setMinWidth(75);
            credit.setMaxWidth(180);
            HBox.setHgrow(debit, Priority.SOMETIMES);
            HBox.setHgrow(credit, Priority.SOMETIMES);
            removeButton.setTooltip(new Tooltip("Remove this line"));
            removeButton.setOnAction(e -> removeAction.run());
            row = new HBox(10, account, debit, credit, removeButton);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            if (existing != null) {
                Account existingAccount = null;
                for (Account possibleAccount : accounts) {
                    if (possibleAccount.getId() == existing.getAccount().getId()) {
                        existingAccount = possibleAccount;
                        break;
                    }
                }
                account.setValue(existingAccount);
                if (existing.getDebit().signum() > 0) debit.setText(existing.getDebit().toPlainString());
                if (existing.getCredit().signum() > 0) credit.setText(existing.getCredit().toPlainString());
            }
            account.valueProperty().addListener((o, old, value) -> changed.run());
            debit.textProperty().addListener((o, old, value) -> changed.run());
            credit.textProperty().addListener((o, old, value) -> changed.run());
        }

        BigDecimal value(TextField field) {
            try { return field.getText() == null || field.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(field.getText().trim()); }
            catch (NumberFormatException ex) { return BigDecimal.ZERO; }
        }

        boolean isInputValid() {
            if (!validAmount(debit.getText()) || !validAmount(credit.getText())) return false;
            BigDecimal debitValue = value(debit);
            BigDecimal creditValue = value(credit);
            return debitValue.signum() == 0 || creditValue.signum() == 0;
        }

        boolean isComplete() {
            BigDecimal debitValue = value(debit);
            BigDecimal creditValue = value(credit);
            return account.getValue() != null
                    && (debitValue.signum() > 0 ^ creditValue.signum() > 0);
        }

        private static boolean validAmount(String text) {
            if (text == null || text.isBlank()) return true;
            try {
                BigDecimal value = new BigDecimal(text.trim());
                return value.signum() >= 0 && value.scale() <= 2 && value.precision() <= 19
                        && value.compareTo(new BigDecimal("1000000000000000")) < 0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    private void viewEntry(JournalEntry selected) {
        if (selected == null) return;
        app.async(() -> app.accounting().entry(user.getId(), selected.getId()), entry -> {
            if (entry == null) {
                app.showError("Journal entry", "This entry is no longer available.");
                reload();
                return;
            }
            StringBuilder details = new StringBuilder("Date: ").append(entry.getDate())
                    .append("\nParticulars: ").append(entry.getParticulars())
                    .append("\nType: ").append(entry.getType())
                    .append("\nSaved currency code: ").append(entry.getCurrency())
                    .append("\n\n");
            for (JournalLine line : entry.getLines()) {
                details.append(line.getAccount().getName()).append("    Debit: ")
                        .append(formatEntryAmount(line.getDebit(), entry.getCurrency()))
                        .append("    Credit: ")
                        .append(formatEntryAmount(line.getCredit(), entry.getCurrency())).append('\n');
            }
            details.append("\nTotal debit: ")
                    .append(formatEntryAmount(entry.getDebitTotal(), entry.getCurrency()))
                    .append("    Total credit: ")
                    .append(formatEntryAmount(entry.getCreditTotal(), entry.getCurrency()));
            Alert detail = new Alert(Alert.AlertType.INFORMATION);
            detail.initOwner(getScene().getWindow());
            detail.setTitle("Journal entry");
            detail.setHeaderText("Entry #" + entry.getId());
            detail.setContentText(details.toString());
            detail.showAndWait();
            recordRecent("JOURNAL_ENTRY", entry.getId(), entry.getParticulars());
        }, ex -> app.showError("Could not load journal entry", MainApp.safeMessage(ex)));
    }

    private void recordRecent(String type, long id, String name) {
        app.async(() -> { app.accounting().recordRecent(user.getId(), type, id, name); return null; },
                ignored -> {}, ex -> app.showError("Could not save recent view", MainApp.safeMessage(ex)));
    }

    private void deleteSelected() {
        JournalEntry selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { app.showError("Delete entry", "Select an entry first."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete journal entry “" + selected.getParticulars() + "” permanently?\n\n"
                        + "Its linked posting lines will also be deleted from the ledger. "
                        + "Other journal entries and accounts will remain unchanged.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(getScene().getWindow());
        confirm.setHeaderText("Delete journal entry?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            app.async(() -> { app.accounting().delete(user.getId(), selected.getId()); return null; },
                    ignored -> {
                        operationStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
                        operationStatus.setText("Journal entry deleted.");
                        reload();
                    }, ex -> app.showError("Could not delete entry", MainApp.safeMessage(ex)));
        }
    }

    private static final class JournalDraft {
        final LocalDate date;
        final String particulars, currency, type;
        final List<JournalLine> lines;
        JournalDraft(LocalDate date, String particulars, String currency, String type, List<JournalLine> lines) {
            this.date=date; this.particulars=particulars; this.currency=currency; this.type=type; this.lines=lines;
        }
    }
}
