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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class JournalEntrySection extends AccountingSection {
    private final TableView<JournalEntry> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label totals = new Label();
    private final Label operationStatus = new Label();

    public JournalEntrySection(MainApp app, User user) {
        super(app, user, "Journal Entry");
        TableColumn<JournalEntry, String> date = column("Date", e -> e.getDate().toString());
        TableColumn<JournalEntry, String> particulars = column("Particulars", JournalEntry::getParticulars);
        TableColumn<JournalEntry, String> currency = column("Currency", e -> app.currencySymbol());
        currency.setPrefWidth(55);
        Label currencyHeading = new Label("Currency");
        currencyHeading.setTooltip(new Tooltip(
                "Display preference only. Saved currency codes are not changed and amounts are not converted."));
        currency.setText(null);
        currency.setGraphic(currencyHeading);
        currency.setCellFactory(column -> new TableCell<JournalEntry, String>() {
            @Override
            protected void updateItem(String symbol, boolean empty) {
                super.updateItem(symbol, empty);
                if (empty) {
                    setText(null);
                    setTooltip(null);
                    return;
                }

                setText(symbol);
                JournalEntry entry = getTableRow() == null ? null : getTableRow().getItem();
                if (entry == null) {
                    setTooltip(null);
                } else {
                    setTooltip(new Tooltip("Saved currency code: " + entry.getCurrency()
                            + ". Display preference only; no conversion."));
                }
            }
        });
        TableColumn<JournalEntry, String> debit = column("Debit", e -> app.formatMoney(e.getDebitTotal()));
        TableColumn<JournalEntry, String> credit = column("Credit", e -> app.formatMoney(e.getCreditTotal()));
        TableColumn<JournalEntry, String> kind = column("Type", JournalEntry::getType);
        table.getColumns().add(date);
        table.getColumns().add(particulars);
        table.getColumns().add(currency);
        table.getColumns().add(debit);
        table.getColumns().add(credit);
        table.getColumns().add(kind);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No journal entries yet. Select Add New Entry to record your first transaction."));
        table.setRowFactory(view -> {
            TableRow<JournalEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) viewEntry(row.getItem());
            });
            return row;
        });

        search.setPromptText("Search particulars");
        Button find = new Button("Search");
        Button refresh = new Button("Refresh");
        Button add = new Button("Add New Entry");
        Button view = new Button("View");
        Button edit = new Button("Edit");
        Button delete = new Button("Delete");
        EntriTheme.primary(add);
        find.setOnAction(e -> reload());
        refresh.setOnAction(e -> { search.clear(); reload(); });
        add.setOnAction(e -> openEditor(null, false));
        view.setOnAction(e -> selectedAction(this::viewEntry, "Select a journal entry to view."));
        edit.setOnAction(e -> selectedAction(entry -> openEditor(entry, false), "Select a journal entry to edit."));
        delete.setOnAction(e -> deleteSelected());
        HBox actions = new HBox(8, add, view, edit, delete, new Region(), search, find, refresh);
        HBox.setHgrow(actions.getChildren().get(4), Priority.ALWAYS);
        actions.setPadding(new Insets(18, 0, 12, 0));
        Label hint = new Label("Balanced entries support multiple debit and credit lines. Double-click a row to view it.");
        EntriTheme.muted(hint);
        VBox content = new VBox(8, actions, table, totals, operationStatus, hint);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    public void addAdjustment() { openEditor(null, true); }
    public void addNewEntry() { openEditor(null, false); }

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
        if ("Particulars".equals(title)) {
            column.setPrefWidth(340);
        } else if ("Currency".equals(title)) {
            column.setPrefWidth(55);
        } else {
            column.setPrefWidth(130);
        }
        return column;
    }

    static String money(BigDecimal amount) { return amount.stripTrailingZeros().toPlainString(); }

    @Override public void reload() {
        String filter = search.getText();
        totals.setText("Loading journal entries...");
        app.async(() -> app.accounting().entries(user.getId(), filter), entries -> {
            table.setItems(FXCollections.observableArrayList(entries));
            Map<String, BigDecimal[]> totalsByCurrency = new TreeMap<>();
            for (JournalEntry entry : entries) {
                BigDecimal[] value = totalsByCurrency.get(entry.getCurrency());
                if (value == null) {
                    value = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                    totalsByCurrency.put(entry.getCurrency(), value);
                }
                value[0] = value[0].add(entry.getDebitTotal());
                value[1] = value[1].add(entry.getCreditTotal());
            }
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, BigDecimal[]> item : totalsByCurrency.entrySet()) {
                BigDecimal[] amount = item.getValue();
                values.add(item.getKey() + " — Debit: " + app.formatMoney(amount[0])
                        + " / Credit: " + app.formatMoney(amount[1]));
            }
            totals.setText("Display preference " + app.currencySymbol()
                    + " (amounts grouped by saved currency; no conversion) — "
                    + (values.isEmpty() ? "0" : String.join("    ", values)));
        }, ex -> app.showError("Could not load journal entries", MainApp.safeMessage(ex)));
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
        DatePicker date = new DatePicker(existing == null ? LocalDate.now() : existing.getDate());
        TextField particulars = new TextField(existing == null ? "" : existing.getParticulars());
        TextField currency = new TextField(existing == null ? app.currencyCode() : existing.getCurrency());
        ComboBox<String> entryType = new ComboBox<>(FXCollections.observableArrayList("STANDARD", "ADJUSTMENT"));
        entryType.setValue(adjustment ? "ADJUSTMENT" : existing == null ? "STANDARD" : existing.getType());
        entryType.setDisable(adjustment);

        ObservableList<LineEditor> lines = FXCollections.observableArrayList();
        VBox lineBox = new VBox(8);
        Label totalsLabel = new Label();
        Label balanceStatus = new Label();
        Runnable updateTotals = () -> updateBalance(lines, totalsLabel, balanceStatus, app.currencyCode());
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
        dialog.getDialogPane().setPrefWidth(760);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (date.getValue() == null) throw new IllegalArgumentException("Choose an entry date.");
                if (particulars.getText() == null || particulars.getText().trim().isEmpty()) {
                    throw new IllegalArgumentException("Enter particulars for this entry.");
                }
                if (particulars.getText().trim().length() > 500) {
                    throw new IllegalArgumentException("Particulars must be no longer than 500 characters.");
                }
                if (currency.getText() == null
                        || !currency.getText().trim().matches("(?i)[a-z]{3}")) {
                    throw new IllegalArgumentException("Enter a three-letter ISO currency code.");
                }
                collectLines(lines);
            } catch (IllegalArgumentException ex) {
                event.consume();
                app.showError("Entry is not ready to save", ex.getMessage());
            }
        });
        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            return new JournalDraft(date.getValue(), particulars.getText(), currency.getText(),
                    entryType.getValue(), collectLines(lines));
        });
        dialog.showAndWait().ifPresent(draft -> app.async(() -> app.accounting().save(user.getId(),
                existing == null ? 0 : existing.getId(), draft.date, draft.particulars, draft.currency,
                draft.type, draft.lines), id -> {
                operationStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
                operationStatus.setText(existing == null ? "Journal entry saved." : "Journal entry updated.");
                reload();
                }, ex -> app.showError("Could not save entry", MainApp.safeMessage(ex))));
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
        pane.setMinWidth(width);
        pane.setPrefWidth(width);
        return pane;
    }

    private static void updateBalance(List<LineEditor> lines, Label totalLabel, Label status,
                                      String currencyCode) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        boolean valid = true;
        for (LineEditor line : lines) {
            debit = debit.add(line.value(line.debit));
            credit = credit.add(line.value(line.credit));
            valid &= line.isInputValid();
        }
        totalLabel.setText("Debit total: " + CurrencyFormatter.format(debit, currencyCode)
                + "     Credit total: " + CurrencyFormatter.format(credit, currencyCode)
                + "     Difference: " + CurrencyFormatter.format(debit.subtract(credit).abs(), currencyCode));
        boolean balanced = valid && debit.signum() > 0 && debit.compareTo(credit) == 0;
        status.setText(balanced ? "Balanced — ready to post"
                : valid ? "Unbalanced — debits must equal credits"
                : "Invalid line amount — use non-negative decimals with up to four places.");
        status.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                + (balanced ? EntriTheme.SUCCESS : EntriTheme.ERROR) + ";");
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
        return Collections.unmodifiableList(lines);
    }

    private static BigDecimal parseAmount(String input) {
        if (input == null || input.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal amount = new BigDecimal(input.trim());
            if (amount.signum() < 0 || amount.scale() > 4 || amount.precision() > 19
                    || amount.compareTo(new BigDecimal("1000000000000000")) >= 0) {
                throw new IllegalArgumentException("Amounts must be non-negative with up to four decimal places.");
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
            debit.setPrefWidth(120);
            credit.setPrefWidth(120);
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

        private static boolean validAmount(String text) {
            if (text == null || text.isBlank()) return true;
            try {
                BigDecimal value = new BigDecimal(text.trim());
                return value.signum() >= 0 && value.scale() <= 4 && value.precision() <= 19
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
                    .append("\nDisplay currency: ").append(app.currencySymbol())
                    .append(" (display preference only; no conversion)\n\n");
            for (JournalLine line : entry.getLines()) {
                details.append(line.getAccount().getName()).append("    Debit: ")
                        .append(app.formatMoney(line.getDebit())).append("    Credit: ")
                        .append(app.formatMoney(line.getCredit())).append('\n');
            }
            details.append("\nTotal debit: ").append(app.formatMoney(entry.getDebitTotal()))
                    .append("    Total credit: ").append(app.formatMoney(entry.getCreditTotal()));
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
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this journal entry permanently?",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(getScene().getWindow());
        confirm.setHeaderText("Delete journal entry");
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
