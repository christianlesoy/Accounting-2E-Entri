package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

public final class WorksheetSection extends AccountingSection {
    private final TableView<WorksheetCalculator.DisplayRow> table = new TableView<>();
    private final TableView<BalanceSheetCalculator.AccountRow> balanceSheetTable = new TableView<>();
    private final Label totals = new Label();
    private final Label balanceStatus = new Label();
    private final VBox balanceSheetStatuses = new VBox(4);
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfYear(1));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final BooleanProperty balanceSheetMode = new SimpleBooleanProperty();
    private final Label fromLabel = new Label("From");
    private final Label toLabel = new Label("To");
    private final Label balanceSheetDateLabel = new Label();
    private final Button refreshButton = new Button("Refresh");
    private final Button exportButton = new Button("Export CSV");
    private final MenuButton optionsButton = new MenuButton("Options");
    private final Button adjustmentButton = new Button("Add Adjustment");
    private final Button addAccountButton = new Button("Add Account");
    private final Button editAccountButton = new Button("Edit Account");
    private final Button deleteAccountButton = new Button("Delete Account");
    private final Runnable addAdjustment;
    private final Runnable addAccount;
    private final Runnable editAccount;
    private final Runnable removeAccount;
    private LocalDate balanceSheetReportDate = LocalDate.now();
    private LocalDate loadedWorksheetStart;
    private LocalDate loadedWorksheetEnd;
    private long reportRequest;
    private BalanceSheetReportState balanceSheetReportState = BalanceSheetReportState.EMPTY;
    private static final String[] GROUPS = {"Trial Balance", "Adjustments", "Adjusted Trial Balance",
            "Income Statement", "Owner's Equity", "Balance Sheet"};

    public WorksheetSection(MainApp app, User user, Runnable addAdjustment,
                            Runnable addAccount, Runnable editAccount, Runnable removeAccount) {
        super(app, user, "12-Column Worksheet",
                "Review trial balance, adjustments, adjusted balances, income, owner's equity, and balance sheet by currency.");
        this.addAdjustment = addAdjustment;
        this.addAccount = addAccount;
        this.editAccount = editAccount;
        this.removeAccount = removeAccount;
        TableColumn<WorksheetCalculator.DisplayRow, String> accountName =
                new TableColumn<>("Account Name");
        accountName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name));
        accountName.setPrefWidth(210);
        accountName.setMinWidth(170);
        table.getColumns().add(accountName);
        for (int i = 0; i < GROUPS.length; i++) {
            TableColumn<WorksheetCalculator.DisplayRow, String> group = new TableColumn<>(GROUPS[i]);
            group.getColumns().add(amountColumn("Debit", i * 2));
            group.getColumns().add(amountColumn("Credit", i * 2 + 1));
            table.getColumns().add(group);
        }
        EntriTheme.table(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.minWidthProperty().bind(Bindings.createDoubleBinding(
                () -> balanceSheetMode.get() ? 460.0 : 1510.0, balanceSheetMode));
        table.setRowFactory(view -> {
            TableRow<WorksheetCalculator.DisplayRow> row = new TableRow<>();
            row.itemProperty().addListener((observable, previous, current) -> styleWorksheetRow(row));
            row.indexProperty().addListener((observable, previous, current) -> styleWorksheetRow(row));
            return row;
        });
        table.setPlaceholder(new Label("No accounts are available."));
        configureBalanceSheetTable();
        refreshButton.setOnAction(e -> reload());
        exportButton.setOnAction(e -> exportWorksheet());
        optionsButton.getItems().setAll(
                new MenuItem("Refresh"),
                new MenuItem("Change Report Date"),
                new MenuItem("Export CSV"));
        optionsButton.getItems().get(0).setOnAction(e -> reload());
        optionsButton.getItems().get(1).setOnAction(e -> changeBalanceSheetDate());
        optionsButton.getItems().get(2).setOnAction(e -> exportWorksheet());
        EntriTheme.primary(adjustmentButton);
        EntriTheme.secondary(addAccountButton);
        EntriTheme.secondary(editAccountButton);
        EntriTheme.secondary(deleteAccountButton);
        EntriTheme.secondary(exportButton);
        optionsButton.setStyle("-fx-background-color: " + EntriTheme.IVORY
                + "; -fx-text-fill: " + EntriTheme.TEXT
                + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 9 16;");
        adjustmentButton.setOnAction(e -> this.addAdjustment.run());
        addAccountButton.setOnAction(e -> this.addAccount.run());
        editAccountButton.setOnAction(e -> this.editAccount.run());
        deleteAccountButton.setOnAction(e -> this.removeAccount.run());
        from.setPromptText("Period start");
        to.setPromptText("Period end");
        balanceSheetDateLabel.setText("As of " + balanceSheetReportDate);
        FlowPane controls = new FlowPane(10, 8, fromLabel, from, toLabel, to,
                balanceSheetDateLabel, refreshButton, exportButton, optionsButton,
                adjustmentButton, addAccountButton, editAccountButton, deleteAccountButton);
        controls.setPadding(new Insets(6, 0, 8, 0));
        Label explanation = new Label("Adjusted trial balance combines trial balance and adjustments. Income and expense accounts flow to the income statement; net income or loss is carried to owner's equity and the balance sheet. The selected currency symbol is a display preference only; amounts are not converted. Account rows and totals retain each entry's saved currency code.");
        explanation.setWrapText(true);
        EntriTheme.muted(explanation);
        totals.setWrapText(true);
        balanceStatus.setWrapText(true);
        ScrollPane horizontalScroll = createReportScroll(table);
        ScrollPane balanceSheetScroll = createReportScroll(balanceSheetTable);
        table.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(balanceSheetMode.get() ? 460.0 : 1510.0,
                        horizontalScroll.getViewportBounds().getWidth()),
                horizontalScroll.viewportBoundsProperty(), balanceSheetMode));
        balanceSheetTable.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(700.0, balanceSheetScroll.getViewportBounds().getWidth()),
                balanceSheetScroll.viewportBoundsProperty()));
        balanceSheetMode.addListener((observable, previous, current) -> {
            reportRequest++;
            updateReportMode();
        });
        updateReportMode();
        VBox content = new VBox(12, controls, explanation, horizontalScroll,
                balanceSheetScroll, totals, balanceStatus, balanceSheetStatuses);
        VBox.setVgrow(horizontalScroll, Priority.ALWAYS);
        VBox.setVgrow(balanceSheetScroll, Priority.ALWAYS);
        setCenter(content);
    }

    private void configureBalanceSheetTable() {
        balanceSheetTable.getColumns().add(balanceSheetColumn(
                "Account", row -> row.name, 240));
        balanceSheetTable.getColumns().add(balanceSheetColumn(
                "Account Type", row -> row.accountType, 150));
        balanceSheetTable.getColumns().add(balanceSheetColumn(
                "Category", row -> row.category, 180));
        balanceSheetTable.getColumns().add(balanceSheetColumn(
                "Currency", row -> row.currency, 100));
        TableColumn<BalanceSheetCalculator.AccountRow, String> amount =
                balanceSheetColumn("Balance", row -> formatSavedCurrency(row.amount, row.currency), 170);
        amount.setStyle("-fx-alignment: CENTER-RIGHT;");
        balanceSheetTable.getColumns().add(amount);
        EntriTheme.table(balanceSheetTable);
        balanceSheetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        balanceSheetTable.setRowFactory(view -> {
            TableRow<BalanceSheetCalculator.AccountRow> row = new TableRow<>();
            row.itemProperty().addListener((observable, previous, current) ->
                    row.setStyle(current != null && current.calculated
                            ? "-fx-font-weight: bold; -fx-background-color: #F5F0DE;" : ""));
            return row;
        });
        balanceSheetTable.setPlaceholder(new Label("No nonzero account balances through this date."));
    }

    private static ScrollPane createReportScroll(TableView<?> reportTable) {
        ScrollPane scroll = new ScrollPane(reportTable);
        scroll.setFitToHeight(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(180);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scroll;
    }

    private TableColumn<BalanceSheetCalculator.AccountRow, String> balanceSheetColumn(
            String title, java.util.function.Function<BalanceSheetCalculator.AccountRow, String> value,
            double width) {
        TableColumn<BalanceSheetCalculator.AccountRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(Math.min(width, 90));
        return column;
    }

    private void styleWorksheetRow(TableRow<WorksheetCalculator.DisplayRow> row) {
        WorksheetCalculator.DisplayRow item = row.getItem();
        if (item == null) {
            row.setStyle("");
        } else if (item.name.startsWith("Net Income / (Loss)")) {
            row.setStyle("-fx-font-weight: bold; -fx-background-color: #F5F0DE;");
        } else {
            row.setStyle("");
        }
    }

    private TableColumn<WorksheetCalculator.DisplayRow, String> amountColumn(String name, int index) {
        TableColumn<WorksheetCalculator.DisplayRow, String> column = new TableColumn<>(name);
        column.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                formatSavedCurrency(d.getValue().values[index], d.getValue().currency)));
        column.setCellFactory(ignored -> new TableCell<WorksheetCalculator.DisplayRow, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            }
        });
        column.setPrefWidth(110);
        column.setMinWidth(85);
        return column;
    }

    private void exportWorksheet() {
        if (balanceSheetMode.get()) {
            long request = ++reportRequest;
            loadBalanceSheet(true, request, balanceSheetReportDate);
            return;
        }

        List<String> headers = new ArrayList<>();
        headers.add("Report Start");
        headers.add("Report End");
        headers.add("Account");
        for (String group : GROUPS) {
            headers.add(group + " Debit");
            headers.add(group + " Credit");
        }

        List<List<?>> rows = new ArrayList<>();
        for (WorksheetCalculator.DisplayRow row : table.getItems()) {
            List<Object> values = new ArrayList<>();
            values.add(loadedWorksheetStart);
            values.add(loadedWorksheetEnd);
            values.add(row.name);
            for (BigDecimal amount : row.values) {
                values.add(amount);
            }
            rows.add(values);
        }
        exportCsv("worksheet.csv", headers, rows);
    }

    private void exportBalanceSheet(BalanceSheetCalculator.Result result, LocalDate reportDate) {
        exportCsv("balance_sheet.csv", BalanceSheetCsv.HEADERS,
                BalanceSheetCsv.rows(result, reportDate));
    }

    private enum BalanceSheetReportState {
        LOADING, LOADED, FAILED, INVALID, EMPTY
    }

    public void setBalanceSheetMode(boolean enabled) {
        balanceSheetMode.set(enabled);
    }

    private void updateReportMode() {
        boolean balanceSheet = balanceSheetMode.get();
        for (int index = 0; index < GROUPS.length; index++) {
            table.getColumns().get(index + 1).setVisible(!balanceSheet);
        }
        Node worksheetScroll = table.getParent();
        Node balanceSheetScroll = balanceSheetTable.getParent();
        if (worksheetScroll != null) {
            worksheetScroll.setVisible(!balanceSheet);
            worksheetScroll.setManaged(!balanceSheet);
        }
        if (balanceSheetScroll != null) {
            balanceSheetScroll.setVisible(balanceSheet);
            balanceSheetScroll.setManaged(balanceSheet);
        }
        balanceStatus.setVisible(!balanceSheet);
        balanceStatus.setManaged(!balanceSheet);
        balanceSheetStatuses.setVisible(balanceSheet);
        balanceSheetStatuses.setManaged(balanceSheet);
        fromLabel.setVisible(!balanceSheet);
        fromLabel.setManaged(!balanceSheet);
        from.setVisible(!balanceSheet);
        from.setManaged(!balanceSheet);
        toLabel.setVisible(!balanceSheet);
        toLabel.setManaged(!balanceSheet);
        to.setVisible(!balanceSheet);
        to.setManaged(!balanceSheet);
        balanceSheetDateLabel.setVisible(balanceSheet);
        balanceSheetDateLabel.setManaged(balanceSheet);
        refreshButton.setVisible(!balanceSheet);
        refreshButton.setManaged(!balanceSheet);
        exportButton.setVisible(!balanceSheet);
        exportButton.setManaged(!balanceSheet);
        optionsButton.setVisible(balanceSheet);
        optionsButton.setManaged(balanceSheet);
        adjustmentButton.setVisible(!balanceSheet);
        adjustmentButton.setManaged(!balanceSheet);
        addAccountButton.setVisible(!balanceSheet);
        addAccountButton.setManaged(!balanceSheet);
        editAccountButton.setVisible(!balanceSheet);
        editAccountButton.setManaged(!balanceSheet);
        deleteAccountButton.setVisible(!balanceSheet);
        deleteAccountButton.setManaged(!balanceSheet);
        to.setPromptText("Period end");
        balanceSheetDateLabel.setText("As of " + balanceSheetReportDate);
        setSectionHeading(balanceSheet ? "Balance Sheet" : "12-Column Worksheet",
                balanceSheet
                        ? "Review assets, liabilities, and equity as of the selected date, by currency."
                        : "Review trial balance, adjustments, adjusted balances, income, owner's equity, and balance sheet by currency.");
    }

    @Override public void reload() {
        boolean balanceSheet = balanceSheetMode.get();
        long request = ++reportRequest;
        if (balanceSheet) {
            loadBalanceSheet(false, request, balanceSheetReportDate);
            return;
        }

        LocalDate periodStart = from.getValue();
        LocalDate periodEnd = to.getValue();
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            app.showError("Invalid reporting period", "The start date must be on or before the end date.");
            return;
        }
        balanceStatus.setText("Loading worksheet...");
        balanceStatus.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + ";");
        app.async(() -> WorksheetCalculator.calculate(
                app.accounting().worksheet(user.getId(), periodStart, periodEnd)), result -> {
            if (!isCurrentRequest(request, false, null)) return;
            loadedWorksheetStart = periodStart;
            loadedWorksheetEnd = periodEnd;
            applyWorksheetResult(result);
        }, ex -> {
            if (!isCurrentRequest(request, false, null)) return;
            app.showError("Could not load worksheet", MainApp.safeMessage(ex));
        });
    }

    private void loadBalanceSheet(boolean exportAfterLoad, long request, LocalDate reportDate) {
        if (reportDate == null) {
            balanceSheetReportState = BalanceSheetReportState.INVALID;
            app.showError("Invalid report date", "Select a valid Balance Sheet report date.");
            return;
        }
        clearReportResults();
        balanceStatus.setText("");
        balanceSheetReportState = BalanceSheetReportState.LOADING;
        balanceSheetStatuses.getChildren().setAll(statusLabel(
                "Loading Balance Sheet...", EntriTheme.SECONDARY, false));
        app.async(() -> BalanceSheetCalculator.calculate(
                app.accounting().worksheet(user.getId(), null, reportDate)), result -> {
            if (!isCurrentRequest(request, true, reportDate)) return;
            applyBalanceSheetResult(result, reportDate);
            if (exportAfterLoad) {
                if (!isCurrentRequest(request, true, reportDate)) return;
                if (balanceSheetReportState == BalanceSheetReportState.LOADED) {
                    exportBalanceSheet(result, reportDate);
                } else if (balanceSheetReportState == BalanceSheetReportState.EMPTY) {
                    app.showError("Export CSV",
                            "The successfully loaded Balance Sheet has no nonblank rows to export.");
                }
            }
        }, ex -> {
            if (!isCurrentRequest(request, true, reportDate)) return;
            clearReportResults();
            balanceSheetReportState = BalanceSheetReportState.FAILED;
            String message;
            String title;
            if (ex instanceof BalanceSheetCalculator.UnsupportedAccountTypeException) {
                message = "Balance Sheet unavailable because an account classification is unsupported.";
                title = "Unsupported account classification";
            } else if (ex instanceof java.sql.SQLException) {
                message = "Balance Sheet unavailable because saved account totals could not be loaded.";
                title = "Could not load Balance Sheet";
            } else {
                message = "Balance Sheet unavailable because its calculation failed.";
                title = "Could not calculate Balance Sheet";
            }
            markBalanceSheetUnavailable(message);
            app.showError(title, MainApp.safeMessage(ex));
        });
    }

    private boolean isCurrentRequest(long request, boolean expectedBalanceSheetMode,
                                     LocalDate expectedReportDate) {
        return request == reportRequest
                && balanceSheetMode.get() == expectedBalanceSheetMode
                && (!expectedBalanceSheetMode
                    || expectedReportDate.equals(balanceSheetReportDate));
    }

    private void changeBalanceSheetDate() {
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.initOwner(getScene() == null ? null : getScene().getWindow());
        dialog.setTitle("Change Balance Sheet Report Date");
        dialog.setHeaderText("Select the date through which to include saved postings.");
        DatePicker candidate = new DatePicker(balanceSheetReportDate);
        candidate.setPromptText("Report date");
        dialog.getDialogPane().setContent(candidate);
        ButtonType confirm = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(confirm, ButtonType.CANCEL);
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirm);
        final LocalDate[] confirmedDate = new LocalDate[1];
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                confirmedDate[0] = parseDate(candidate);
            } catch (IllegalArgumentException ex) {
                balanceSheetReportState = BalanceSheetReportState.INVALID;
                event.consume();
                showInvalidReportDateWarning(ex.getMessage());
            }
        });
        dialog.setResultConverter(button -> button == confirm ? confirmedDate[0] : null);
        Optional<LocalDate> selectedDate = dialog.showAndWait();
        if (selectedDate.isEmpty()) return;
        balanceSheetReportDate = selectedDate.get();
        balanceSheetDateLabel.setText("As of " + balanceSheetReportDate);
        long request = ++reportRequest;
        loadBalanceSheet(false, request, balanceSheetReportDate);
    }

    private static LocalDate parseDate(DatePicker picker) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter a report date.");
        }
        try {
            LocalDate date = picker.getConverter().fromString(text);
            if (date == null) throw new IllegalArgumentException("Enter a valid report date.");
            return date;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Enter a valid Balance Sheet report date.", ex);
        }
    }

    private void showInvalidReportDateWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (getScene() != null) alert.initOwner(getScene().getWindow());
        alert.setTitle("Invalid report date");
        alert.setHeaderText("The Balance Sheet date was not changed.");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void clearReportResults() {
        table.setItems(FXCollections.observableArrayList());
        balanceSheetTable.setItems(FXCollections.observableArrayList());
        totals.setText("");
        balanceStatus.setText("");
        balanceStatus.setStyle("");
        balanceSheetStatuses.getChildren().clear();
    }

    private void markBalanceSheetUnavailable(String message) {
        totals.setText(message);
        balanceSheetStatuses.getChildren().setAll(
                statusLabel(message, EntriTheme.ERROR, true));
    }

    private void applyBalanceSheetResult(BalanceSheetCalculator.Result result, LocalDate reportDate) {
        balanceSheetTable.setItems(FXCollections.observableArrayList(result.rows));
        balanceSheetTable.setPlaceholder(new Label(result.hasPostedEntries
                ? "No nonzero account balances through this date."
                : "No accounts or posted journal entries through this date."));

        List<List<?>> csvRows = BalanceSheetCsv.rows(result, reportDate);
        balanceSheetReportState = csvRows.isEmpty()
                ? BalanceSheetReportState.EMPTY : BalanceSheetReportState.LOADED;
        if (!result.hasPostedEntries) {
            totals.setText("As of " + reportDate + " — No posted balances.");
            balanceSheetStatuses.getChildren().setAll(statusLabel(
                    "No posted journal entries through this report date.", EntriTheme.SECONDARY, false));
            return;
        }

        List<String> summaries = new ArrayList<>();
        List<Label> statuses = new ArrayList<>();
        for (Map.Entry<String, BalanceSheetCalculator.Totals> entry
                : result.totalsByCurrency.entrySet()) {
            String currency = entry.getKey();
            BalanceSheetCalculator.Totals values = entry.getValue();
            summaries.add(currency + " — Assets: " + formatSavedCurrency(values.assets, currency)
                    + " | Liabilities: " + formatSavedCurrency(values.liabilities, currency)
                    + " | Owner's Equity (before earnings): " + formatSavedCurrency(values.equity, currency)
                    + " | Revenue: " + formatSavedCurrency(values.revenue, currency)
                    + " | Expenses: " + formatSavedCurrency(values.expenses, currency)
                    + " | Net Income / (Loss): " + formatSavedCurrency(values.netIncome, currency)
                    + " | Liabilities + Equity + Earnings: "
                    + formatSavedCurrency(values.liabilitiesEquityAndEarnings, currency)
                    + " | Difference: " + formatSavedCurrency(values.difference, currency));
            String status = currency + " — " + (values.isBalanced()
                    ? "Balanced"
                    : "Unbalanced — Difference: " + formatSavedCurrency(values.difference, currency));
            statuses.add(statusLabel(status,
                    values.isBalanced() ? EntriTheme.SUCCESS : EntriTheme.ERROR, true));
        }
        totals.setText("Balance Sheet totals as of " + reportDate + "\n"
                + String.join("\n", summaries));
        balanceSheetStatuses.getChildren().setAll(statuses);
    }

    private void applyWorksheetResult(WorksheetCalculator.Result result) {
        table.setItems(FXCollections.observableArrayList(result.rows));
        List<String> summaries = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> totalEntry : result.totalsByCurrency.entrySet()) {
            String currency = totalEntry.getKey();
            BigDecimal[] sums = totalEntry.getValue();
            List<String> sections = new ArrayList<>();
            for (int i = 0; i < GROUPS.length; i++) {
                sections.add(GROUPS[i] + ": "
                        + formatSavedCurrency(sums[i * 2], currency) + " / "
                        + formatSavedCurrency(sums[i * 2 + 1], currency));
            }
            summaries.add(currency + " — " + String.join("    ", sections));
        }
        totals.setText("Totals (Debit / Credit) by currency  |  "
                + (summaries.isEmpty() ? "No balances" : String.join("       ", summaries)));
        balanceStatus.setText(!result.hasPostedEntries
                ? "No posted journal entries in this reporting period. Add entries before relying on this worksheet."
                : result.balanced
                    ? "Balanced — trial balance, adjustments, adjusted trial balance, income statement and balance sheet agree by currency. Net income is carried into equity and the balance sheet."
                    : "Unbalanced — review the period's trial balance or adjustments before relying on this worksheet.");
        balanceStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                + (result.hasPostedEntries && result.balanced
                    ? EntriTheme.SUCCESS : EntriTheme.ERROR) + ";");
    }

    private static Label statusLabel(String text, String color, boolean bold) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + color
                + (bold ? "; -fx-font-weight: bold;" : ";"));
        return label;
    }

    private static String formatSavedCurrency(BigDecimal amount, String currency) {
        String code = currency == null || currency.isBlank() ? "—"
                : currency.trim().toUpperCase(Locale.ROOT);
        if (CurrencyFormatter.isSupported(code)) {
            return code + " " + CurrencyFormatter.format(amount, code);
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return code + " " + format.format(amount);
    }
}
