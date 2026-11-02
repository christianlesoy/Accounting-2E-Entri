package main.java.com.entri;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class AccountsSection extends AccountingSection {
    private final LedgerSection ledger;
    private final TableView<Account> table = new TableView<>();
    private final Label operationStatus = new Label();

    public AccountsSection(MainApp app, User user, LedgerSection ledger) {
        super(app, user, "Accounts", "View and manage your chart of accounts.");
        this.ledger = ledger;

        addColumn("Code", Account::getCode, 120);
        addColumn("Account Name", Account::getName, 320);
        addColumn("Classification", Account::getType, 160);
        EntriTheme.table(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No accounts are available."));

        Button add = new Button("Add Account");
        Button edit = new Button("Edit Account");
        Button delete = new Button("Delete Account");
        Button refresh = new Button("Refresh");
        operationStatus.setWrapText(true);
        add.setOnAction(event -> {
            operationStatus.setText("");
            ledger.addNewAccount(ownerWindow(), this::showSuccess);
        });
        edit.setOnAction(event -> {
            operationStatus.setText("");
            ledger.editAccountDialog(ownerWindow(), this::showSuccess);
        });
        delete.setOnAction(event -> {
            operationStatus.setText("");
            ledger.removeAccountDialog(ownerWindow());
        });
        EntriTheme.primary(add);
        EntriTheme.secondary(edit);
        EntriTheme.secondary(delete);
        EntriTheme.secondary(refresh);
        refresh.setOnAction(event -> reload());

        FlowPane actions = new FlowPane(10, 8, add, edit, delete, refresh);
        actions.setPadding(new Insets(6, 0, 8, 0));
        VBox content = new VBox(10, actions, operationStatus, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
    }

    private void addColumn(String heading,
                           java.util.function.Function<Account, String> value, double width) {
        TableColumn<Account, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }

    private Window ownerWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private void showSuccess(String message) {
        operationStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
        operationStatus.setText(message);
    }

    @Override public void reload() {
        app.async(() -> app.accounting().accounts(user.getId()), accounts -> {
            table.setItems(FXCollections.observableArrayList(accounts));
            table.setPlaceholder(new Label("No accounts are available."));
        }, error -> {
            app.showError("Could not refresh accounts",
                    MainApp.safeMessage(error) + "\n\nPreviously loaded accounts remain visible and may be outdated.");
        });
    }
}
