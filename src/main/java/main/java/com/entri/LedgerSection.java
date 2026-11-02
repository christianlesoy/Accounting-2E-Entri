package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.function.Consumer;

public final class LedgerSection extends AccountingSection {
    private final ComboBox<Account> accounts = new ComboBox<>();
    private final TableView<LedgerRegisterRow> table = new TableView<>();
    private final TableView<TAccountRow> debitTable = new TableView<>();
    private final TableView<TAccountRow> creditTable = new TableView<>();
    private final Label totals = new Label("Debit total: 0    Credit total: 0");
    private final Label debitSideTotals = new Label();
    private final Label creditSideTotals = new Label();
    private final Label operationStatus = new Label();
    private final Label selectedAccountInfo = new Label();
    private final TextField search = new TextField();
    private final TextField accountSearch = new TextField();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private List<AccountingRepository.LedgerLine> allRows = new ArrayList<>();
    private List<Account> allAccounts = new ArrayList<>();
    private LedgerCalculator.Result ledgerResult;
    private Long requestedAccountId;
    private long accountLoadRequest;
    private long ledgerRequest;
    private boolean updatingAccountSelection;

    public LedgerSection(MainApp app, User user) {
        super(app, user, "Ledger",
                "Choose an account to review its dated debit and credit activity and running balances.");
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> loadAccounts());
        Button addAccount = new Button("Add Account");
        Button editAccount = new Button("Edit Account");
        Button removeAccount = new Button("Delete Account");
        EntriTheme.primary(addAccount);
        EntriTheme.secondary(editAccount);
        EntriTheme.secondary(removeAccount);
        addAccount.setOnAction(e -> addNewAccount());
        editAccount.setOnAction(e -> editSelectedAccount());
        removeAccount.setOnAction(e -> removeSelectedAccount());
        editAccount.disableProperty().bind(accounts.valueProperty().isNull());
        removeAccount.disableProperty().bind(accounts.valueProperty().isNull());
        search.setPromptText("Filter explanations");
        accountSearch.setPromptText("Search accounts");
        accountSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!updatingAccountSelection) filterAccounts();
        });
        Button filter = new Button("Search");
        filter.setOnAction(e -> loadLedger());
        Button clearFilters = new Button("Clear filters");
        clearFilters.setOnAction(e -> {
            clearAccountSearch();
            search.clear();
            clearDate(fromDate);
            clearDate(toDate);
            if (accounts.getValue() != null) loadLedger();
            else applyFilter();
        });
        EntriTheme.secondary(clearFilters);
        accounts.setPromptText("Select an account");
        accounts.setOnAction(e -> {
            if (!updatingAccountSelection) loadLedger();
        });
        accountSearch.setMinWidth(130);
        accountSearch.setPrefWidth(200);
        accountSearch.setMaxWidth(280);
        accounts.setMinWidth(150);
        accounts.setPrefWidth(220);
        accounts.setMaxWidth(320);
        search.setMinWidth(150);
        search.setPrefWidth(210);
        search.setMaxWidth(300);
        search.setOnAction(e -> applyFilter());
        fromDate.setPromptText("Start date");
        toDate.setPromptText("End date");
        fromDate.setPrefWidth(145);
        toDate.setPrefWidth(145);
        FlowPane accountActions = new FlowPane(10, 8, addAccount, editAccount, removeAccount, refresh);
        FlowPane controls = new FlowPane(10, 8,
                new Label("Account"), accountSearch, accounts,
                new Label("From"), fromDate, new Label("To"), toDate, search, filter, clearFilters);
        selectedAccountInfo.setWrapText(true);
        EntriTheme.muted(selectedAccountInfo);
        VBox controlArea = new VBox(8, accountActions, controls);
        controlArea.setPadding(new Insets(6, 0, 8, 0));
        table.getColumns().add(registerCol("Date", row -> row.date, 105));
        table.getColumns().add(registerCol("Reference", row -> row.reference, 115));
        table.getColumns().add(registerCol("Account", row -> row.account, 190));
        table.getColumns().add(registerCol("Particulars / explanation", row -> row.explanation, 250));
        table.getColumns().add(registerCol("Currency", row -> row.currency, 80));
        table.getColumns().add(registerCol("Debit", row -> row.debit, 130));
        table.getColumns().add(registerCol("Credit", row -> row.credit, 130));
        table.getColumns().add(registerCol("Running balance", row -> row.balance, 150));
        EntriTheme.table(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Select an account to see its ledger."));
        totals.setWrapText(true);
        operationStatus.setWrapText(true);
        debitSideTotals.setWrapText(true);
        creditSideTotals.setWrapText(true);
        totals.setPadding(new Insets(12, 0, 0, 0));
        Label explanation = new Label("Balances show actual net debits and credits. Expected normal side follows the account classification; contra-account and drawings classifications are not stored separately. Currency totals are not converted.");
        explanation.setWrapText(true);
        EntriTheme.muted(explanation);
        TabPane views = new TabPane();
        Tab tAccount = new Tab("T-Account");
        tAccount.setClosable(false);
        tAccount.setContent(createTAccountView());
        Tab register = new Tab("Ledger Register");
        register.setClosable(false);
        VBox registerContent = new VBox(8, table, totals, explanation);
        VBox.setVgrow(table, Priority.ALWAYS);
        register.setContent(registerContent);
        views.getTabs().addAll(tAccount, register);
        VBox content = new VBox(8, controlArea, selectedAccountInfo, operationStatus, views);
        VBox.setVgrow(views, Priority.ALWAYS);
        setCenter(content);
    }

    private BorderPane createTAccountView() {
        configureTAccountTable(debitTable, "Debit");
        configureTAccountTable(creditTable, "Credit");
        VBox debitSide = new VBox(8, sideHeading("DEBIT"), debitTable, debitSideTotals);
        VBox creditSide = new VBox(8, sideHeading("CREDIT"), creditTable, creditSideTotals);
        VBox.setVgrow(debitTable, Priority.ALWAYS);
        VBox.setVgrow(creditTable, Priority.ALWAYS);
        VBox.setVgrow(debitSide, Priority.ALWAYS);
        VBox.setVgrow(creditSide, Priority.ALWAYS);
        BorderPane responsiveView = new BorderPane();
        responsiveView.setMaxWidth(Double.MAX_VALUE);
        HBox sides = new HBox(14);
        VBox stackedSides = new VBox(14);
        Runnable arrangeSides = () -> {
            sides.getChildren().clear();
            stackedSides.getChildren().clear();
            if (getWidth() < 980) {
                Separator divider = new Separator(javafx.geometry.Orientation.HORIZONTAL);
                divider.setStyle("-fx-background-color: " + EntriTheme.OLIVE + ";");
                stackedSides.getChildren().setAll(debitSide, divider, creditSide);
                responsiveView.setCenter(stackedSides);
            } else {
                Separator divider = new Separator(javafx.geometry.Orientation.VERTICAL);
                divider.setStyle("-fx-background-color: " + EntriTheme.OLIVE + ";");
                sides.getChildren().setAll(debitSide, divider, creditSide);
                HBox.setHgrow(debitSide, Priority.ALWAYS);
                HBox.setHgrow(creditSide, Priority.ALWAYS);
                responsiveView.setCenter(sides);
            }
        };
        widthProperty().addListener((observable, oldWidth, newWidth) -> arrangeSides.run());
        arrangeSides.run();
        return responsiveView;
    }

    private Label sideHeading(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-background-color: " + EntriTheme.OLIVE + "; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold; -fx-padding: 10 12;"
                + " -fx-background-radius: 6;");
        return label;
    }

    private void configureTAccountTable(TableView<TAccountRow> side, String amountHeading) {
        side.getColumns().add(tAccountColumn("Date", row -> row.date, 105));
        side.getColumns().add(tAccountColumn("Reference", row -> row.reference, 105));
        side.getColumns().add(tAccountColumn("Explanation", row -> row.explanation, 230));
        side.getColumns().add(tAccountColumn("Currency", row -> row.currency, 85));
        TableColumn<TAccountRow, String> amount = tAccountColumn(amountHeading,
                row -> formatAmount(row.amount, row.currency), 130);
        amount.setStyle("-fx-alignment: CENTER-RIGHT;");
        side.getColumns().add(amount);
        EntriTheme.table(side);
        side.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        side.setPlaceholder(new Label("No postings on this side."));
    }

    private TableColumn<TAccountRow, String> tAccountColumn(
            String title, java.util.function.Function<TAccountRow, String> value, double width) {
        TableColumn<TAccountRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(Math.min(width, 90));
        return column;
    }

    private TableColumn<LedgerRegisterRow, String> registerCol(
            String title, java.util.function.Function<LedgerRegisterRow, String> value, double width) {
        TableColumn<LedgerRegisterRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        if ("Debit".equals(title) || "Credit".equals(title) || "Running balance".equals(title)) {
            column.setCellFactory(ignored -> new TableCell<LedgerRegisterRow, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                }
            });
        }
        column.setPrefWidth(width);
        column.setMinWidth(Math.min(width, 90));
        return column;
    }

    private static String formatAmount(BigDecimal value, String currency) {
        String amount = CurrencyFormatter.isSupported(currency)
                ? CurrencyFormatter.format(value.abs(), currency)
                : value.abs().stripTrailingZeros().toPlainString();
        return currency + " " + amount;
    }

    private static String formatBalance(BigDecimal value, String currency) {
        String side = LedgerCalculator.actualSide(value);
        if ("ZERO".equals(side)) return formatAmount(BigDecimal.ZERO, currency) + " (Zero)";
        return formatAmount(value.abs(), currency) + ("DEBIT".equals(side) ? " Dr" : " Cr");
    }

    private void loadAccounts() {
        long request = ++accountLoadRequest;
        boolean openedById = requestedAccountId != null;
        Long previousAccountId = requestedAccountId != null
                ? requestedAccountId : accounts.getValue() == null ? null : accounts.getValue().getId();
        clearLedgerDisplay("Loading accounts...");
        accounts.setDisable(true);
        app.async(() -> app.accounting().accounts(user.getId()), list -> {
            if (request != accountLoadRequest) return;
            accounts.setDisable(false);
            allAccounts = list;
            requestedAccountId = null;
            if (previousAccountId != null) {
                Account requestedAccount = findAccount(list, previousAccountId);
                if (requestedAccount == null) {
                    setAccountItems(List.of(), null);
                    selectedAccountInfo.setText("");
                    operationStatus.setText("The selected account is no longer available.");
                    app.showError("Ledger account unavailable",
                            "This account was deleted or is not available to your account.");
                    return;
                }
                if (openedById) clearAccountSearch();
            }
            List<Account> matches = matchingAccounts();
            Account selection = previousAccountId == null ? null : findAccount(matches, previousAccountId);
            if (selection == null && previousAccountId == null && !matches.isEmpty()) {
                selection = matches.get(0);
            }
            setAccountItems(matches, selection);
            if (list.isEmpty()) {
                selectedAccountInfo.setText("");
                operationStatus.setText("No ledger accounts are available.");
                table.setPlaceholder(new Label("No accounts available."));
            } else if (accounts.getValue() == null) {
                selectedAccountInfo.setText("");
                operationStatus.setText("No accounts match the account search.");
                table.setPlaceholder(new Label("No account is selected."));
            } else {
                loadLedger();
            }
        }, ex -> {
            if (request != accountLoadRequest) return;
            accounts.setDisable(false);
            allAccounts = new ArrayList<>();
            setAccountItems(List.of(), null);
            selectedAccountInfo.setText("");
            clearLedgerDisplay("Results unavailable: accounts could not be loaded.");
            app.showError("Could not load accounts", MainApp.safeMessage(ex));
        });
    }

    private void filterAccounts() {
        Long oldId = accounts.getValue() == null ? null : accounts.getValue().getId();
        List<Account> matches = matchingAccounts();
        Account selection = oldId == null ? null : findAccount(matches, oldId);
        if (selection == null && oldId == null && !matches.isEmpty()) {
            selection = matches.get(0);
        }
        setAccountItems(matches, selection);
        Long newId = accounts.getValue() == null ? null : accounts.getValue().getId();
        if (oldId == null || !oldId.equals(newId)) {
            if (accounts.getValue() != null) loadLedger();
            else {
                selectedAccountInfo.setText("");
                clearLedgerDisplay(allAccounts.isEmpty()
                        ? "No ledger accounts are available." : "No accounts match the account search.");
                table.setPlaceholder(new Label(allAccounts.isEmpty()
                        ? "No accounts available." : "No account matches the account search."));
            }
        }
    }

    private List<Account> matchingAccounts() {
        String query = accountSearch.getText() == null ? ""
                : accountSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        List<Account> matches = new ArrayList<>();
        for (Account account : allAccounts) {
            if (account.getName().toLowerCase(java.util.Locale.ROOT).contains(query)
                    || account.getCode().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                matches.add(account);
            }
        }
        return matches;
    }

    private void setAccountItems(List<Account> items, Account selection) {
        updatingAccountSelection = true;
        try {
            accounts.setItems(FXCollections.observableArrayList(items));
            if (selection == null) accounts.getSelectionModel().clearSelection();
            else accounts.getSelectionModel().select(selection);
        } finally {
            updatingAccountSelection = false;
        }
    }

    private void clearAccountSearch() {
        updatingAccountSelection = true;
        try {
            clearAccountSearch();
        } finally {
            updatingAccountSelection = false;
        }
    }

    private static void clearDate(DatePicker picker) {
        picker.setValue(null);
        picker.getEditor().clear();
    }

    public void addNewAccount() {
        addNewAccount(ownerWindow());
    }

    public void addNewAccount(Window owner) {
        addNewAccount(owner, null);
    }

    public void addNewAccount(Window owner, Consumer<String> successFeedback) {
        showAccountEditor(null, owner, successFeedback);
    }

    public void editSelectedAccount() {
        Account selected = accounts.getValue();
        if (selected == null) {
            app.showError("Edit Account", "Select an account first.");
            return;
        }
        showAccountEditor(selected, ownerWindow(), null);
    }

    public void editAccountDialog() {
        editAccountDialog(ownerWindow());
    }

    public void editAccountDialog(Window owner) {
        editAccountDialog(owner, null);
    }

    public void editAccountDialog(Window owner, Consumer<String> successFeedback) {
        app.async(() -> app.accounting().accounts(user.getId()), list -> {
            if (list.isEmpty()) {
                app.showError("Edit Account", "There are no ledger accounts to edit.");
                return;
            }
            ChoiceDialog<Account> chooser = new ChoiceDialog<>(list.get(0), list);
            setOwner(chooser, owner);
            chooser.setTitle("Edit Account");
            chooser.setHeaderText("Select the account to edit");
            chooser.setContentText("Ledger account:");
            chooser.showAndWait().ifPresent(selected ->
                    showAccountEditor(selected, owner, successFeedback));
        }, ex -> app.showError("Could not load ledger accounts", MainApp.safeMessage(ex)));
    }

    private void showAccountEditor(Account existing, Window owner, Consumer<String> successFeedback) {
        Dialog<AccountDraft> dialog = new Dialog<>();
        setOwner(dialog, owner);
        dialog.setTitle(existing == null ? "Add Account" : "Edit Account");
        ButtonType saveType = new ButtonType(existing == null ? "Add Account" : "Save Changes",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
        TextField code = new TextField();
        code.setPromptText("For example, 1300");
        code.setText(existing == null ? "" : existing.getCode());
        TextField name = new TextField();
        name.setPromptText("Account name");
        name.setText(existing == null ? "" : existing.getName());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                Arrays.asList("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE")));
        type.setPromptText("Select classification");
        if (existing != null) type.setValue(existing.getType());
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(12);
        fields.add(new Label("Account code"), 0, 0);
        fields.add(code, 1, 0);
        fields.add(new Label("Account name"), 0, 1);
        fields.add(name, 1, 1);
        fields.add(new Label("Classification"), 0, 2);
        fields.add(type, 1, 2);
        if (existing != null) {
            Label warning = new Label("Changing classification is blocked when this account has posted journal activity.");
            warning.setWrapText(true);
            warning.setMaxWidth(420);
            fields.add(warning, 0, 3, 2, 1);
        }
        Button reset = new Button(existing == null ? "Clear Fields" : "Reset");
        EntriTheme.secondary(reset);
        reset.setTooltip(new Tooltip(existing == null
                ? "Clear the new account fields." : "Restore the account's original values."));
        reset.setOnAction(event -> {
            code.setText(existing == null ? "" : existing.getCode());
            name.setText(existing == null ? "" : existing.getName());
            type.setValue(existing == null ? null : existing.getType());
        });
        fields.add(reset, 1, existing == null ? 3 : 4);
        fields.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(fields);
        Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                validateNewAccount(code.getText(), name.getText(), type.getValue());
            } catch (IllegalArgumentException ex) {
                event.consume();
                app.showError("Account is not ready to save", ex.getMessage());
            }
        });
        dialog.setResultConverter(button -> button == saveType
                ? new AccountDraft(code.getText(), name.getText(), type.getValue()) : null);
        dialog.showAndWait().ifPresent(draft -> app.async(() -> existing == null
                ? app.accounting().addAccount(user.getId(), draft.code, draft.name, draft.type)
                : app.accounting().updateAccount(user.getId(), existing.getId(),
                        draft.code, draft.name, draft.type), updated -> {
                    String successMessage = existing == null
                            ? "Account " + updated.getCode() + " — " + updated.getName() + " created."
                            : "Account " + updated.getCode() + " — " + updated.getName() + " updated.";
                    operationStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
                    operationStatus.setText(successMessage);
                    if (successFeedback != null) successFeedback.accept(successMessage);
                    accountSearch.clear();
                    requestedAccountId = updated.getId();
                    app.refreshAccountingViews();
                }, ex -> app.showError(existing == null ? "Could not add ledger account"
                        : "Could not update ledger account", MainApp.safeMessage(ex))));
    }

    private static void validateNewAccount(String code, String name, String type) {
        if (code == null || !code.trim().matches("[A-Za-z0-9-]{1,20}")) {
            throw new IllegalArgumentException("Account code must use 1 to 20 letters, numbers, or hyphens.");
        }
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Enter an account name (up to 100 characters).");
        }
        if (type == null) {
            throw new IllegalArgumentException("Choose an account classification.");
        }
    }

    public void removeAccountDialog() {
        removeAccountDialog(ownerWindow());
    }

    public void removeAccountDialog(Window owner) {
        app.async(() -> app.accounting().accounts(user.getId()), list -> {
            if (list.isEmpty()) {
                app.showError("Remove Ledger", "There are no ledger accounts to remove.");
                return;
            }
            ChoiceDialog<Account> chooser = new ChoiceDialog<>(list.get(0), list);
            setOwner(chooser, owner);
            chooser.setTitle("Delete Account");
            chooser.setHeaderText("Select the account to delete");
            chooser.setContentText("Ledger account:");
            chooser.showAndWait().ifPresent(selected -> confirmAccountRemoval(selected, owner));
        }, ex -> app.showError("Could not load ledger accounts", MainApp.safeMessage(ex)));
    }

    private void removeSelectedAccount() {
        Account selected = accounts.getValue();
        if (selected == null) {
            app.showError("Delete Account", "Select an account first.");
            return;
        }
        confirmAccountRemoval(selected, ownerWindow());
    }

    private void confirmAccountRemoval(Account selected, Window owner) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + selected.getCode() + " — " + selected.getName()
                        + "?\n\nAccounts with posted journal activity cannot be deleted. "
                        + "No linked accounting records will be removed.",
                ButtonType.CANCEL, ButtonType.OK);
        setOwner(confirm, owner);
        confirm.setTitle("Delete Account");
        confirm.setHeaderText("Delete this ledger account?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        app.async(() -> {
            app.accounting().removeAccount(user.getId(), selected.getId());
            return null;
        }, ignored -> {
            operationStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
            operationStatus.setText("Ledger account deleted. Linked accounting records were not deleted.");
            app.refreshAccountingViews();
        }, ex -> app.showError("Could not remove ledger account", MainApp.safeMessage(ex)));
    }

    private Window ownerWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static void setOwner(Dialog<?> dialog, Window owner) {
        if (owner != null) dialog.initOwner(owner);
    }

    private static final class AccountDraft {
        final String code, name, type;
        AccountDraft(String code, String name, String type) {
            this.code = code;
            this.name = name;
            this.type = type;
        }
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
        if (account == null) {
            clearLedgerDisplay(allAccounts.isEmpty()
                    ? "No ledger accounts are available." : "Select an account to view its ledger.");
            selectedAccountInfo.setText("");
            table.setPlaceholder(new Label(allAccounts.isEmpty()
                    ? "No accounts available." : "Select an account to see its ledger."));
            return;
        }

        LocalDate start;
        LocalDate end;
        try {
            start = dateValue(fromDate, "start");
            end = dateValue(toDate, "end");
        } catch (IllegalArgumentException ex) {
            clearLedgerDisplay("Results unavailable: " + ex.getMessage());
            app.showError("Invalid date", ex.getMessage());
            return;
        }
        if (start != null && end != null && start.isAfter(end)) {
            String message = "The start date must be on or before the end date.";
            clearLedgerDisplay("Results unavailable: " + message);
            app.showError("Invalid date range", message);
            return;
        }

        clearLedgerDisplay("Loading ledger...");
        long request = ++ledgerRequest;
        long accountId = account.getId();
        selectedAccountInfo.setText(account.getCode() + " — " + account.getName()
                + " (" + account.getType() + "); standard expected normal side: "
                + LedgerCalculator.expectedNormalSide(account.getType()).toLowerCase(java.util.Locale.ROOT)
                + ". Actual balances use net debits less credits. Contra/drawings subtype is not stored; "
                + "verify account-specific normal side where applicable.");
        table.setPlaceholder(new Label("Loading ledger postings..."));
        app.async(() -> app.accounting().ledgerPeriod(user.getId(), accountId, start, end), result -> {
            if (request != ledgerRequest || accounts.getValue() == null
                    || accounts.getValue().getId() != accountId) return;
            ledgerResult = result;
            allRows = result.periodRows;
            applyFilter();
            app.async(() -> {
                app.accounting().recordRecent(user.getId(), "LEDGER_ACCOUNT", accountId, account.getName());
                return null;
            }, ignored -> {}, ex -> app.showError("Could not save recent view", MainApp.safeMessage(ex)));
        }, ex -> {
            if (request != ledgerRequest || accounts.getValue() == null
                    || accounts.getValue().getId() != accountId) return;
            clearLedgerDisplay("Results unavailable: ledger postings could not be loaded.");
            table.setPlaceholder(new Label("Ledger results unavailable."));
            app.showError("Could not load ledger", MainApp.safeMessage(ex));
        });
    }

    private void applyFilter() {
        if (ledgerResult == null) {
            table.setItems(FXCollections.observableArrayList());
            debitTable.setItems(FXCollections.observableArrayList());
            creditTable.setItems(FXCollections.observableArrayList());
            return;
        }
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        List<AccountingRepository.LedgerLine> visible = new ArrayList<>();
        for (AccountingRepository.LedgerLine row : allRows) {
            if (row.explanation.toLowerCase(java.util.Locale.ROOT).contains(query)) {
                visible.add(row);
            }
        }

        List<LedgerRegisterRow> registerRows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : ledgerResult.openingByCurrency.entrySet()) {
            registerRows.add(LedgerRegisterRow.balance("Opening Balance", entry.getKey(),
                    entry.getValue(), selectedAccount()));
        }
        for (AccountingRepository.LedgerLine row : visible) {
            registerRows.add(LedgerRegisterRow.posting(row));
        }
        for (Map.Entry<String, BigDecimal> entry : ledgerResult.closingByCurrency.entrySet()) {
            registerRows.add(LedgerRegisterRow.balance("Closing Balance", entry.getKey(),
                    entry.getValue(), selectedAccount()));
        }
        table.setItems(FXCollections.observableArrayList(registerRows));
        table.setPlaceholder(new Label(ledgerResult.openingByCurrency.isEmpty()
                ? "No postings for this account and date range."
                : "No period postings; opening balance is carried forward."));

        List<TAccountRow> debitRows = new ArrayList<>();
        List<TAccountRow> creditRows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : ledgerResult.openingByCurrency.entrySet()) {
            TAccountRow opening = TAccountRow.opening(entry.getKey(), entry.getValue());
            if (entry.getValue().signum() < 0) creditRows.add(opening);
            else debitRows.add(opening);
        }
        for (AccountingRepository.LedgerLine row : visible) {
            if (row.debit.signum() > 0) debitRows.add(TAccountRow.posting(row, row.debit));
            if (row.credit.signum() > 0) creditRows.add(TAccountRow.posting(row, row.credit));
        }
        debitTable.setItems(FXCollections.observableArrayList(debitRows));
        creditTable.setItems(FXCollections.observableArrayList(creditRows));

        totals.setText("Opening balances: " + formatBalances(ledgerResult.openingByCurrency)
                + "\nFull period totals: " + formatPeriodTotals()
                + "\nClosing balances: " + formatBalances(ledgerResult.closingByCurrency));
        debitSideTotals.setText("Opening balances are placed on their actual Dr/Cr side. "
                + "Full period debits: " + formatBalances(ledgerResult.debitsByCurrency));
        creditSideTotals.setText("Full period credits: " + formatBalances(ledgerResult.creditsByCurrency)
                + "\nClosing balances: " + formatBalances(ledgerResult.closingByCurrency));
        if (!query.isEmpty()) {
            Map<String, BigDecimal[]> visibleTotals = sumVisible(visible);
            String visibleSummary = formatTotals(visibleTotals);
            totals.setText(totals.getText() + "\nVisible-row subtotals (search only): "
                    + visibleSummary);
            debitSideTotals.setText(debitSideTotals.getText()
                    + "\nVisible-row debit subtotal (search only): " + sideTotal(visibleTotals, true));
            creditSideTotals.setText(creditSideTotals.getText()
                    + "\nVisible-row credit subtotal (search only): " + sideTotal(visibleTotals, false));
        }

        if (visible.isEmpty()) {
            operationStatus.setText(ledgerResult.periodRows.isEmpty()
                    ? ledgerResult.openingByCurrency.isEmpty()
                        ? "No ledger postings match the selected account and period."
                        : "No period postings; opening balance remains the closing balance."
                    : "No period postings match the explanation search. Full balances are unchanged.");
        } else {
            operationStatus.setText("");
        }
    }

    private Map<String, BigDecimal[]> sumVisible(List<AccountingRepository.LedgerLine> rows) {
        Map<String, BigDecimal[]> result = new TreeMap<>();
        for (AccountingRepository.LedgerLine row : rows) {
            BigDecimal[] total = result.computeIfAbsent(row.currency,
                    ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            total[0] = total[0].add(row.debit);
            total[1] = total[1].add(row.credit);
        }
        return result;
    }

    private String formatPeriodTotals() {
        List<String> values = new ArrayList<>();
        for (String currency : ledgerResult.debitsByCurrency.keySet()) {
            values.add(currency + " — Debit: "
                    + formatAmount(ledgerResult.debitsByCurrency.get(currency), currency)
                    + "    Credit: "
                    + formatAmount(ledgerResult.creditsByCurrency.get(currency), currency));
        }
        return values.isEmpty() ? "none" : String.join("    ", values);
    }

    private static String formatBalances(Map<String, BigDecimal> balances) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : balances.entrySet()) {
            values.add(formatBalance(entry.getValue(), entry.getKey()));
        }
        return values.isEmpty() ? "none" : String.join("    ", values);
    }

    private static String formatTotals(Map<String, BigDecimal[]> totals) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : totals.entrySet()) {
            values.add(entry.getKey() + " — Debit: "
                    + formatAmount(entry.getValue()[0], entry.getKey())
                    + "    Credit: " + formatAmount(entry.getValue()[1], entry.getKey()));
        }
        return values.isEmpty() ? "none" : String.join("    ", values);
    }

    private static String sideTotal(Map<String, BigDecimal[]> totals, boolean debit) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : totals.entrySet()) {
            BigDecimal amount = entry.getValue()[debit ? 0 : 1];
            values.add(entry.getKey() + " " + formatAmount(amount, entry.getKey()));
        }
        return values.isEmpty() ? "none" : String.join("    ", values);
    }

    private Account selectedAccount() {
        return accounts.getValue();
    }

    private static LocalDate dateValue(DatePicker picker, String field) {
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
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Enter a valid " + field + " date.", ex);
        }
    }

    private void clearLedgerDisplay(String message) {
        ledgerRequest++;
        allRows = new ArrayList<>();
        ledgerResult = null;
        table.setItems(FXCollections.observableArrayList());
        debitTable.setItems(FXCollections.observableArrayList());
        creditTable.setItems(FXCollections.observableArrayList());
        totals.setText("");
        debitSideTotals.setText("");
        creditSideTotals.setText("");
        operationStatus.setText(message == null ? "" : message);
        operationStatus.setStyle(message != null && message.startsWith("Results unavailable")
                ? "-fx-text-fill: " + EntriTheme.ERROR + ";" : "");
    }

    @Override public void reload() { loadAccounts(); }

    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    public void openAccount(long accountId) {
        requestedAccountId = accountId;
        loadAccounts();
    }

    private static final class TAccountRow {
        final String date;
        final String reference;
        final String explanation;
        final String currency;
        final BigDecimal amount;

        private TAccountRow(String date, String reference, String explanation,
                            String currency, BigDecimal amount) {
            this.date = date;
            this.reference = reference;
            this.explanation = explanation;
            this.currency = currency;
            this.amount = amount;
        }

        static TAccountRow opening(String currency, BigDecimal balance) {
            return new TAccountRow("", "", "Opening Balance",
                    currency, balance.abs());
        }

        static TAccountRow posting(AccountingRepository.LedgerLine line, BigDecimal amount) {
            return new TAccountRow(line.date.toString(), "Entry #" + line.entryId,
                    line.explanation, line.currency, amount);
        }
    }

    private static final class LedgerRegisterRow {
        final String date;
        final String reference;
        final String account;
        final String explanation;
        final String currency;
        final String debit;
        final String credit;
        final String balance;

        private LedgerRegisterRow(String date, String reference, String account, String explanation,
                                  String currency, String debit, String credit, String balance) {
            this.date = date;
            this.reference = reference;
            this.account = account;
            this.explanation = explanation;
            this.currency = currency;
            this.debit = debit;
            this.credit = credit;
            this.balance = balance;
        }

        static LedgerRegisterRow posting(AccountingRepository.LedgerLine line) {
            return new LedgerRegisterRow(line.date.toString(),
                    "Entry #" + line.entryId + " / Line #" + line.lineId,
                    line.accountCode + " — " + line.account,
                    line.explanation,
                    line.currency,
                    formatAmount(line.debit, line.currency),
                    formatAmount(line.credit, line.currency),
                    formatBalance(line.balance, line.currency));
        }

        static LedgerRegisterRow balance(String label, String currency, BigDecimal value, Account account) {
            String accountLabel = account == null ? "" : account.getCode() + " — " + account.getName();
            String debit = value.signum() > 0
                    ? formatAmount(value, currency)
                    : value.signum() == 0 ? formatAmount(BigDecimal.ZERO, currency) : "—";
            String credit = value.signum() < 0
                    ? formatAmount(value.abs(), currency)
                    : value.signum() == 0 ? formatAmount(BigDecimal.ZERO, currency) : "—";
            return new LedgerRegisterRow("", "", accountLabel, label, currency,
                    debit, credit, formatBalance(value, currency));
        }
    }
}
