package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class WorksheetSection extends AccountingSection {
    private final TableView<WorksheetDisplay> table = new TableView<>();
    private final Label totals = new Label();
    private final Label balanceStatus = new Label();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfYear(1));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final Runnable addAdjustment;
    private static final String[] GROUPS = {"Trial Balance", "Adjustments", "Adjusted Trial Balance",
            "Income Statement", "Owner's Equity", "Balance Sheet"};

    public WorksheetSection(MainApp app, User user, Runnable addAdjustment) {
        super(app, user, "12-column worksheet");
        this.addAdjustment = addAdjustment;
        TableColumn<WorksheetDisplay, String> accountName = new TableColumn<>("Account Name");
        accountName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().name));
        accountName.setPrefWidth(210);
        table.getColumns().add(accountName);
        for (int i = 0; i < GROUPS.length; i++) {
            TableColumn<WorksheetDisplay, String> group = new TableColumn<>(GROUPS[i]);
            group.getColumns().add(amountColumn("Debit", i * 2));
            group.getColumns().add(amountColumn("Credit", i * 2 + 1));
            table.getColumns().add(group);
        }
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No accounts are available."));
        Button refresh = new Button("Reload worksheet");
        refresh.setOnAction(e -> reload());
        Button adjustment = new Button("Add Adjustment");
        EntriTheme.primary(adjustment);
        adjustment.setOnAction(e -> this.addAdjustment.run());
        from.setPromptText("Period start");
        to.setPromptText("Period end");
        HBox controls = new HBox(10, new Label("From"), from, new Label("To"), to, refresh, adjustment);
        controls.setPadding(new Insets(16, 0, 8, 0));
        Label explanation = new Label("Adjusted trial balance combines trial balance and adjustments. Income and expense accounts flow to the income statement; net income or loss is carried to owner's equity and the balance sheet. The selected currency symbol is a display preference only; amounts are not converted. Account rows and totals retain each entry's saved currency code.");
        explanation.setWrapText(true);
        EntriTheme.muted(explanation);
        VBox content = new VBox(12, controls, explanation, table, totals, balanceStatus);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    private TableColumn<WorksheetDisplay, String> amountColumn(String name, int index) {
        TableColumn<WorksheetDisplay, String> column = new TableColumn<>(name);
        column.setCellValueFactory(d -> new ReadOnlyStringWrapper(fmt(d.getValue().values[index])));
        column.setPrefWidth(110);
        return column;
    }

    @Override public void reload() {
        LocalDate periodStart = from.getValue();
        LocalDate periodEnd = to.getValue();
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            app.showError("Invalid reporting period", "The start date must be on or before the end date.");
            return;
        }
        balanceStatus.setText("Loading worksheet...");
        balanceStatus.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + ";");
        app.async(() -> app.accounting().worksheet(user.getId(), periodStart, periodEnd), data -> {
            boolean hasPostedEntries = false;
            for (AccountingRepository.WorksheetRow row : data) {
                if (row.entryType != null && row.debit != null && row.credit != null) {
                    hasPostedEntries = true;
                    break;
                }
            }
            Map<String, Aggregate> accounts = new LinkedHashMap<>();
            for (AccountingRepository.WorksheetRow row : data) {
                String currency = row.currency == null ? "—" : row.currency;
                String key = row.id + ":" + currency;
                Aggregate a = accounts.get(key);
                if (a == null) {
                    a = new Aggregate(row.name + " (" + currency + ")", row.type, currency);
                    accounts.put(key, a);
                }
                if (row.entryType == null || row.debit == null || row.credit == null) continue;
                if ("ADJUSTMENT".equals(row.entryType)) {
                    a.adjustDebit = a.adjustDebit.add(row.debit);
                    a.adjustCredit = a.adjustCredit.add(row.credit);
                } else {
                    a.trialDebit = a.trialDebit.add(row.debit);
                    a.trialCredit = a.trialCredit.add(row.credit);
                }
            }
            List<WorksheetDisplay> display = new ArrayList<>();
            Map<String, BigDecimal[]> totalsByCurrency = new TreeMap<>();
            Map<String, BigDecimal> netIncomeByCurrency = new TreeMap<>();
            for (Aggregate a : accounts.values()) {
                BigDecimal[] sums = totalsByCurrency.get(a.currency);
                if (sums == null) {
                    sums = new BigDecimal[12];
                    Arrays.fill(sums, BigDecimal.ZERO);
                    totalsByCurrency.put(a.currency, sums);
                }
                BigDecimal trialNet = a.trialDebit.subtract(a.trialCredit);
                BigDecimal adjNet = a.adjustDebit.subtract(a.adjustCredit);
                BigDecimal adjustedNet = trialNet.add(adjNet);
                WorksheetDisplay row = new WorksheetDisplay(a.name);
                putNet(row.values, 0, trialNet, isDebitNormal(a.type));
                row.values[2] = a.adjustDebit;
                row.values[3] = a.adjustCredit;
                putNet(row.values, 4, adjustedNet, isDebitNormal(a.type));
                if ("REVENUE".equals(a.type) || "EXPENSE".equals(a.type)) {
                    putNet(row.values, 6, adjustedNet, isDebitNormal(a.type));
                    BigDecimal currentIncome = netIncomeByCurrency.get(a.currency);
                    if (currentIncome == null) currentIncome = BigDecimal.ZERO;
                    netIncomeByCurrency.put(a.currency, currentIncome.add(adjustedNet.negate()));
                }
                if ("EQUITY".equals(a.type)) putNet(row.values, 8, adjustedNet, false);
                if ("ASSET".equals(a.type) || "LIABILITY".equals(a.type) || "EQUITY".equals(a.type)) {
                    putNet(row.values, 10, adjustedNet, isDebitNormal(a.type));
                }
                for (int i = 0; i < sums.length; i++) sums[i] = sums[i].add(row.values[i]);
                display.add(row);
            }
            for (Map.Entry<String, BigDecimal> incomeEntry : netIncomeByCurrency.entrySet()) {
                String currency = incomeEntry.getKey();
                BigDecimal netIncome = incomeEntry.getValue();
                WorksheetDisplay netIncomeRow = new WorksheetDisplay("Net Income / (Loss) (" + currency + ")");
                if (netIncome.signum() > 0) {
                    netIncomeRow.values[6] = netIncome;
                    netIncomeRow.values[9] = netIncome;
                    netIncomeRow.values[11] = netIncome;
                } else if (netIncome.signum() < 0) {
                    BigDecimal loss = netIncome.negate();
                    netIncomeRow.values[7] = loss;
                    netIncomeRow.values[8] = loss;
                    netIncomeRow.values[10] = loss;
                }
                BigDecimal[] sums = totalsByCurrency.get(currency);
                for (int i = 0; i < sums.length; i++) sums[i] = sums[i].add(netIncomeRow.values[i]);
                display.add(netIncomeRow);
            }
            table.setItems(FXCollections.observableArrayList(display));
            List<String> summaries = new ArrayList<>();
            for (Map.Entry<String, BigDecimal[]> totalEntry : totalsByCurrency.entrySet()) {
                String currency = totalEntry.getKey();
                BigDecimal[] sums = totalEntry.getValue();
                List<String> sections = new ArrayList<>();
                for (int i = 0; i < GROUPS.length; i++) {
                    sections.add(GROUPS[i] + ": " + fmt(sums[i * 2]) + " / " + fmt(sums[i * 2 + 1]));
                }
                summaries.add(currency + " — " + String.join("    ", sections));
            }
            totals.setText("Totals (Debit / Credit) by currency  |  " + String.join("       ", summaries));
            boolean balanced = true;
            for (BigDecimal[] sums : totalsByCurrency.values()) {
                if (sums[0].compareTo(sums[1]) != 0
                        || sums[2].compareTo(sums[3]) != 0
                        || sums[4].compareTo(sums[5]) != 0
                        || sums[6].compareTo(sums[7]) != 0
                        || sums[10].compareTo(sums[11]) != 0) {
                    balanced = false;
                    break;
                }
            }
            balanceStatus.setText(!hasPostedEntries
                    ? "No posted journal entries in this reporting period. Add entries before relying on this worksheet."
                    : balanced
                    ? "Balanced — trial balance, adjustments, adjusted trial balance, income statement and balance sheet agree by currency. Net income is carried into equity and the balance sheet."
                    : "Unbalanced — review the period's trial balance or adjustments before relying on this worksheet.");
            balanceStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                    + (hasPostedEntries && balanced ? EntriTheme.SUCCESS : EntriTheme.ERROR) + ";");
        }, ex -> app.showError("Could not load worksheet", MainApp.safeMessage(ex)));
    }

    private static boolean isDebitNormal(String type) { return "ASSET".equals(type) || "EXPENSE".equals(type); }
    private static void putNet(BigDecimal[] values, int index, BigDecimal net, boolean debitNormal) {
        BigDecimal normal = debitNormal ? net : net.negate();
        values[index] = normal.signum() > 0 ? normal : BigDecimal.ZERO;
        values[index + 1] = normal.signum() < 0 ? normal.negate() : BigDecimal.ZERO;
    }
    private String fmt(BigDecimal value) { return app.formatMoney(value); }

    private static final class Aggregate {
        final String name, type, currency;
        BigDecimal trialDebit = BigDecimal.ZERO, trialCredit = BigDecimal.ZERO;
        BigDecimal adjustDebit = BigDecimal.ZERO, adjustCredit = BigDecimal.ZERO;
        Aggregate(String name, String type, String currency) {
            this.name=name; this.type=type; this.currency=currency;
        }
    }

    private static final class WorksheetDisplay {
        final String name;
        final BigDecimal[] values = new BigDecimal[12];
        WorksheetDisplay(String name) { this.name=name; Arrays.fill(values, BigDecimal.ZERO); }
    }
}
