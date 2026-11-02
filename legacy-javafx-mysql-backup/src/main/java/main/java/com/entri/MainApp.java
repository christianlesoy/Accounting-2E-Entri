package main.java.com.entri;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.net.URL;

public final class MainApp extends Application {
    private final AuthService auth = new AuthService();
    private final AccountingService accounting = new AccountingService();
    private final UserSettingsRepository settings = new UserSettingsRepository();
    private Stage stage;
    private Image appLogo;
    private String currencyCode = "USD";
    private Menu currencyMenu;
    private User currentUser;
    private BorderPane shell;
    private ScrollPane pageScroller;
    private Label pageTitle;
    private VBox sidebar;
    private OverviewSection dashboard;
    private JournalEntrySection journal;
    private LedgerSection ledger;
    private WorksheetSection worksheet;
    private RecentViewsSection recent;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        URL logoResource = MainApp.class.getResource("/images/logo.png");
        if (logoResource == null) throw new IllegalStateException("Missing app logo: /images/logo.png");
        appLogo = new Image(logoResource.toExternalForm());
        stage.getIcons().add(appLogo);
        stage.setTitle("Entri — Accounting Management System");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        if (getParameters().getRaw().contains("--preview")) showPreview();
        else showLogin(false, null);
        stage.show();
    }

    AccountingService accounting() { return accounting; }

    String currencyCode() {
        return currencyCode;
    }

    String currencySymbol() {
        return CurrencyFormatter.symbolFor(currencyCode);
    }

    String formatMoney(BigDecimal amount) {
        return CurrencyFormatter.format(amount, currencyCode);
    }

    <T> void async(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> success.accept(task.getValue()));
        task.setOnFailed(e -> failure.accept(task.getException()));
        Thread thread = new Thread(task, "entri-background-task");
        thread.setDaemon(true);
        thread.start();
    }

    void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (stage != null) alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank()
                ? "The operation could not be completed." : message);
        alert.showAndWait();
    }

    static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
                ? "The operation could not be completed." : message;
    }

    private ImageView logoImage(double size) {
        ImageView imageView = new ImageView(appLogo);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setAccessibleText("Entri logo");
        return imageView;
    }

    private void showLogin(boolean registration, String feedback) {
        Label brandName = new Label("Entri");
        brandName.setStyle("-fx-font-size: 38px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.OLIVE + ";");
        HBox brand = new HBox(12, logoImage(62), brandName);
        brand.setAlignment(Pos.CENTER);
        Label title = new Label(registration ? "Create your account" : "Welcome back");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + EntriTheme.TEXT + ";");
        Label subtitle = new Label(registration ? "Set up your accounting workspace."
                : "Sign in to manage your accounting records.");
        EntriTheme.muted(subtitle);

        TextField username = new TextField();
        username.setPromptText(registration ? "Username" : "Username or email");
        username.setAccessibleText(registration ? "Username" : "Username or email");
        TextField email = new TextField();
        email.setPromptText("Email address");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText("Confirm password");

        Label message = new Label(feedback == null ? "" : feedback);
        message.setWrapText(true);
        message.setMinHeight(22);
        message.setStyle("-fx-text-fill: " + (feedback != null && feedback.startsWith("Account created")
                ? EntriTheme.SUCCESS : EntriTheme.ERROR) + ";");
        Label passwordHint = new Label("Use at least 10 characters for your password.");
        EntriTheme.muted(passwordHint);
        VBox fields = new VBox(12);
        fields.getChildren().add(username);
        if (registration) fields.getChildren().addAll(email, password, passwordHint, confirmation);
        else fields.getChildren().add(password);
        Button submit = new Button(registration ? "Register" : "Log In");
        Button alternate = new Button(registration ? "Back to Login" : "Create an Account");
        Button preview = new Button("Explore a sample preview");
        EntriTheme.primary(submit);
        EntriTheme.secondary(alternate);
        EntriTheme.secondary(preview);
        submit.setMaxWidth(Double.MAX_VALUE);
        alternate.setMaxWidth(Double.MAX_VALUE);
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.setVisible(!registration);
        preview.setManaged(!registration);
        preview.setOnAction(e -> showPreview());
        submit.setDefaultButton(true);
        alternate.setOnAction(e -> showLogin(!registration, null));
        VBox card = new VBox(15, brand, title, subtitle, fields, message, submit, alternate, preview);
        card.setPadding(new Insets(38));
        card.setMaxWidth(460);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: "
                + EntriTheme.BORDER + "; -fx-border-radius: 16;");
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(28));
        root.setStyle(EntriTheme.BASE);
        submit.setOnAction(e -> {
            submit.setDisable(true);
            alternate.setDisable(true);
            message.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + ";");
            message.setText(registration ? "Creating your account..." : "Signing in...");
            String accountName = username.getText();
            char[] pass = password.getText().toCharArray();
            char[] confirm = confirmation.getText().toCharArray();
            String accountEmail = email.getText();
            password.clear();
            confirmation.clear();
            if (registration) {
                async(() -> { User created = auth.register(accountName, accountEmail, pass, confirm); return created; },
                        created -> {
                            clearSecrets(pass, confirm);
                            showLogin(false, "Account created. You can now sign in.");
                        }, ex -> {
                            clearSecrets(pass, confirm);
                            submit.setDisable(false);
                            alternate.setDisable(false);
                            message.setStyle("-fx-text-fill: " + EntriTheme.ERROR + ";");
                            message.setText(safeMessage(ex));
                        });
            } else {
                async(() -> auth.authenticate(accountName, pass), user -> {
                    clearSecrets(pass, confirm);
                    currentUser = user;
                    currencyCode = "USD";
                    loadCurrencyPreference(user.getId());
                }, ex -> {
                    clearSecrets(pass, confirm);
                    submit.setDisable(false);
                    alternate.setDisable(false);
                    message.setStyle("-fx-text-fill: " + EntriTheme.ERROR + ";");
                    message.setText(safeMessage(ex));
                });
            }
        });
        stage.setScene(new Scene(root, 1100, 760));
    }

    private void showPreview() {
        BorderPane previewShell = new BorderPane();
        previewShell.setStyle(EntriTheme.BASE);

        BorderPane toolbar = new BorderPane();
        toolbar.setPadding(new Insets(14, 24, 14, 24));
        toolbar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent "
                + EntriTheme.BORDER + " transparent;");
        Label title = new Label("Dashboard");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.TEXT + ";");
        Label previewBadge = new Label("SAMPLE PREVIEW");
        previewBadge.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 7 10; -fx-background-radius: 12;");
        VBox navigation = new VBox(6);
        BorderPane page = new BorderPane();
        Label avatar = new Label("AM");
        avatar.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 7 9; -fx-background-radius: 18;");
        MenuItem profileItem = new MenuItem("View Profile");
        profileItem.setOnAction(event -> showError("Sample preview", "Profile details are available after you sign in."));
        MenuItem settingsItem = new MenuItem("Account Settings");
        settingsItem.setOnAction(event -> showError("Sample preview", "Account settings are read-only in the sample preview."));
        MenuItem developersItem = new MenuItem("Developers");
        developersItem.setOnAction(event -> {
            title.setText("Developers");
            page.setCenter(previewPage("developers"));
        });
        MenuItem logoutItem = new MenuItem("Back to Login");
        logoutItem.setStyle("-fx-text-fill: #DC2626;");
        logoutItem.setOnAction(event -> showLogin(false, null));
        MenuButton accountMenu = new MenuButton("Alex Morgan", avatar);
        accountMenu.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.TEXT + ";");
        accountMenu.getItems().addAll(profileItem, settingsItem, developersItem,
                new SeparatorMenuItem(), logoutItem);
        HBox right = new HBox(12, previewBadge, accountMenu);
        right.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setLeft(new HBox(8, logoImage(34), new Label("ENTRI")));
        toolbar.setCenter(title);
        toolbar.setRight(right);
        previewShell.setTop(toolbar);

        VBox sidebar = new VBox(8);
        sidebar.setPadding(new Insets(20, 12, 14, 12));
        sidebar.setPrefWidth(218);
        sidebar.setStyle("-fx-background-color: " + EntriTheme.OLIVE + ";");
        Label logoName = new Label("Entri");
        logoName.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.CORNSILK + ";");
        HBox logo = new HBox(10, logoImage(42), logoName);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPadding(new Insets(0, 0, 18, 12));
        for (String[] item : new String[][] {
                {"Dashboard", "dashboard"}, {"Journal Entry", "journal"}, {"Ledger", "ledger"},
                {"12-Column Worksheet", "worksheet"}, {"Recent Views", "recent"}, {"Developers", "developers"}
        }) {
            Button button = new Button(item[0]);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                    + EntriTheme.CORNSILK + "; -fx-font-weight: bold; -fx-padding: 12 10;"
                    + " -fx-background-radius: 7;");
            button.setOnAction(event -> {
                title.setText(item[0]);
                page.setCenter(previewPage(item[1]));
                for (Node node : navigation.getChildren()) {
                    if (node instanceof Button) {
                        Button navButton = (Button) node;
                        boolean active = navButton == button;
                        navButton.setStyle("-fx-background-color: "
                                + (active ? EntriTheme.IVORY : "transparent") + "; -fx-text-fill: "
                                + (active ? EntriTheme.TEXT : EntriTheme.CORNSILK)
                                + "; -fx-font-weight: bold; -fx-padding: 12 10; -fx-background-radius: 7;");
                    }
                }
            });
            navigation.getChildren().add(button);
        }
        sidebar.getChildren().addAll(logo, navigation, new Separator());
        previewShell.setLeft(sidebar);
        ScrollPane scroller = new ScrollPane(page);
        scroller.setFitToWidth(true);
        scroller.setStyle("-fx-background: " + EntriTheme.CORNSILK + "; -fx-background-color: "
                + EntriTheme.CORNSILK + ";");
        previewShell.setCenter(scroller);
        Button dashboardButton = null;
        for (Node node : navigation.getChildren()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                if ("Dashboard".equals(button.getText())) {
                    dashboardButton = button;
                    break;
                }
            }
        }
        if (dashboardButton == null) {
            throw new IllegalStateException("Dashboard navigation button is missing.");
        }
        dashboardButton.fire();
        stage.setScene(new Scene(previewShell, 1440, 900));
    }

    private VBox previewPage(String section) {
        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        Label notice = new Label("Sample data only — this preview is read-only. Nothing is saved.");
        notice.setWrapText(true);
        notice.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-padding: 12; -fx-background-radius: 8; -fx-font-weight: bold;");
        content.getChildren().add(notice);
        if ("journal".equals(section)) {
            content.getChildren().addAll(
                    previewHeading("Journal Entry", "Example transactions for the current period."),
                    previewJournalTable());
        } else if ("ledger".equals(section)) {
            content.getChildren().addAll(
                    previewHeading("Ledger", "Example account activity and balanced totals."),
                    previewLedgerTable());
        } else if ("worksheet".equals(section)) {
            content.getChildren().addAll(
                    previewHeading("12 Columns", "Example balances across the six worksheet sections."),
                    previewWorksheetTable());
        } else if ("recent".equals(section)) {
            content.getChildren().addAll(
                    previewHeading("Recent Views", "Recently saved entries and accounting files."),
                    previewRecentTable());
        } else if ("developers".equals(section)) {
            content.getChildren().addAll(
                    previewHeading("Developers", "The people behind Entri."),
                    previewCard("Development Team", new Label(String.join("\n\n", DeveloperInfo.TEAM))));
        } else {
            content.getChildren().add(previewHeading("Accounting Dashboard",
                    "A summary of your accounting workspace."));
            GridPane financialGrid = new GridPane();
            financialGrid.setHgap(18);
            financialGrid.setVgap(18);
            financialGrid.add(previewCard("Journal Entry", previewJournalTable()), 0, 0);
            financialGrid.add(previewCard("Ledger", previewLedgerTable()), 1, 0);
            ColumnConstraints journalColumn = new ColumnConstraints();
            journalColumn.setPercentWidth(50);
            ColumnConstraints ledgerColumn = new ColumnConstraints();
            ledgerColumn.setPercentWidth(50);
            financialGrid.getColumnConstraints().addAll(journalColumn, ledgerColumn);
            VBox worksheetCard = previewCard("12 Columns Worksheet", previewWorksheetTable());
            VBox recentCard = previewCard("Recent Views", previewRecentTable());
            content.getChildren().addAll(financialGrid, worksheetCard, recentCard);
        }
        return content;
    }

    private TableView<String[]> previewJournalTable() {
        return previewTable(new String[] {"Date", "Particulars", "Currency", "Debit", "Credit"},
                new String[][] {
                        {"2026-10-04", "Owner investment", "USD", "5,000.00", "5,000.00"},
                        {"2026-10-09", "Office supplies", "USD", "125.00", "125.00"},
                        {"2026-10-16", "Service revenue", "USD", "850.00", "850.00"},
                        {"TOTAL", "", "USD", "5,975.00", "5,975.00"}
                });
    }

    private TableView<String[]> previewLedgerTable() {
        return previewTable(new String[] {"Date", "Explanation", "Debit", "Credit"},
                new String[][] {
                        {"2026-10-04", "Owner investment — Cash", "5,000.00", "—"},
                        {"2026-10-04", "Owner investment — Owner's Capital", "—", "5,000.00"},
                        {"2026-10-09", "Office supplies — Supplies Expense", "125.00", "—"},
                        {"2026-10-09", "Office supplies — Cash", "—", "125.00"},
                        {"2026-10-16", "Services provided — Cash", "850.00", "—"},
                        {"2026-10-16", "Services provided — Service Revenue", "—", "850.00"},
                        {"TOTAL", "", "5,975.00", "5,975.00"}
                });
    }

    private Node previewWorksheetTable() {
        String[] groups = {"Trial Balance", "Adjustment", "Adjusted Trial Balance",
                "Income Statement", "Owner's Equity", "Balance Sheet"};
        String[][] rows = {
                {"Cash", "5,725.00", "—", "—", "—", "5,725.00", "—", "—", "—", "—", "—", "5,725.00", "—"},
                {"Supplies Expense", "125.00", "—", "—", "—", "125.00", "—", "125.00", "—", "—", "—", "—", "—"},
                {"Owner's Capital", "—", "5,000.00", "—", "—", "—", "5,000.00", "—", "—", "—", "5,000.00", "—", "5,000.00"},
                {"Service Revenue", "—", "850.00", "—", "—", "—", "850.00", "—", "850.00", "—", "—", "—", "—"},
                {"Net Income", "—", "—", "—", "—", "—", "—", "725.00", "—", "—", "725.00", "—", "725.00"},
                {"Ending Owner's Equity", "—", "—", "—", "—", "—", "—", "—", "—", "5,725.00", "—", "—", "—"},
                {"TOTAL", "5,850.00", "5,850.00", "—", "—", "5,850.00", "5,850.00", "850.00", "850.00", "5,725.00", "5,725.00", "5,725.00", "5,725.00"}
        };
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        TableColumn<String[], String> account = previewColumn("Account Name", 0);
        account.setPrefWidth(155);
        table.getColumns().add(account);
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            TableColumn<String[], String> group = new TableColumn<>(groups[groupIndex]);
            TableColumn<String[], String> debit = previewColumn("Debit", groupIndex * 2 + 1);
            TableColumn<String[], String> credit = previewColumn("Credit", groupIndex * 2 + 2);
            debit.setPrefWidth(94);
            credit.setPrefWidth(94);
            group.getColumns().add(debit);
            group.getColumns().add(credit);
            table.getColumns().add(group);
        }
        table.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        table.setRowFactory(view -> previewTableRow(rows.length));
        table.setPrefWidth(1290);
        table.setPrefHeight(44 + rows.length * 30);
        ScrollPane horizontalScroll = new ScrollPane(table);
        horizontalScroll.setFitToHeight(true);
        horizontalScroll.setPannable(true);
        horizontalScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        horizontalScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        horizontalScroll.setPrefViewportHeight(270);
        horizontalScroll.setStyle("-fx-background: white; -fx-background-color: white;");
        return horizontalScroll;
    }

    private TableView<String[]> previewRecentTable() {
        String[][] rows = {
                {"October journal entries", "Journal", "2026-10-27 14:32"},
                {"Cash ledger", "Ledger", "2026-10-27 13:15"},
                {"October 12-column worksheet", "Worksheet", "2026-10-26 16:40"}
        };
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(previewColumn("File Name", 0));
        table.getColumns().add(previewColumn("Type", 1));
        table.getColumns().add(previewColumn("Last Modified", 2));
        TableColumn<String[], String> actions = new TableColumn<>("Action");
        actions.setCellValueFactory(data -> new ReadOnlyStringWrapper(""));
        actions.setCellFactory(column -> new TableCell<>() {
            private final Button view = new Button("View");
            private final Button edit = new Button("Edit");
            private final HBox buttons = new HBox(6, view, edit);
            {
                EntriTheme.secondary(view);
                edit.setDisable(true);
                edit.setTooltip(new Tooltip("Sample preview is read-only."));
                view.setOnAction(event -> showError("Sample preview",
                        "This is illustrative sample data. Sign in to view your saved records."));
            }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : buttons);
            }
        });
        actions.setPrefWidth(150);
        table.getColumns().add(actions);
        table.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        table.setRowFactory(view -> previewTableRow(rows.length));
        table.setPrefHeight(44 + rows.length * 36);
        return table;
    }

    private VBox previewHeading(String heading, String subtitle) {
        Label title = new Label(heading);
        EntriTheme.title(title);
        Label description = new Label(subtitle);
        EntriTheme.muted(description);
        VBox box = new VBox(6, title, description);
        box.setPadding(new Insets(4, 0, 4, 0));
        return box;
    }

    private VBox previewCard(String heading, Node body) {
        Label title = new Label(heading);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.TEXT + ";");
        VBox card = new VBox(12, title, body);
        card.setPadding(new Insets(18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: "
                + EntriTheme.BORDER + "; -fx-border-radius: 10;");
        return card;
    }

    private TableView<String[]> previewTable(String[] headers, String[][] rows) {
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int index = 0; index < headers.length; index++) {
            table.getColumns().add(previewColumn(headers[index], index));
        }
        table.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        table.setRowFactory(view -> previewTableRow(rows.length));
        table.setPrefHeight(Math.max(130, 44 + rows.length * 32));
        return table;
    }

    private TableColumn<String[], String> previewColumn(String title, int valueIndex) {
        TableColumn<String[], String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue()[valueIndex]));
        if (title.contains("Debit") || title.contains("Credit") || title.contains("Balance")) {
            column.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas';");
        }
        return column;
    }

    private TableRow<String[]> previewTableRow(int rowCount) {
        TableRow<String[]> row = new TableRow<>();
        Runnable updateStyle = () -> {
            String[] item = row.getItem();
            if (item == null) {
                row.setStyle("");
                return;
            }
            boolean total = row.getIndex() == rowCount - 1
                    && "TOTAL".equalsIgnoreCase(item[0]);
            row.setStyle(total
                    ? "-fx-background-color: white; -fx-font-weight: bold; -fx-border-color: "
                            + EntriTheme.BORDER + " transparent transparent transparent;"
                    : row.getIndex() % 2 == 0
                            ? "-fx-background-color: white;"
                            : "-fx-background-color: #F9FAFB;");
        };
        row.itemProperty().addListener((observable, previous, current) -> updateStyle.run());
        row.indexProperty().addListener((observable, previous, current) -> updateStyle.run());
        return row;
    }

    private static void clearSecrets(char[] password, char[] confirmation) {
        java.util.Arrays.fill(password, '\0');
        java.util.Arrays.fill(confirmation, '\0');
    }

    private void showDashboard() {
        if (currentUser == null) { showLogin(false, null); return; }
        shell = new BorderPane();
        shell.setStyle(EntriTheme.BASE);
        shell.setTop(buildToolbar());
        sidebar = buildSidebar();
        shell.setLeft(sidebar);
        pageScroller = new ScrollPane();
        pageScroller.setFitToWidth(true);
        pageScroller.setStyle("-fx-background: " + EntriTheme.CORNSILK + "; -fx-background-color: "
                + EntriTheme.CORNSILK + ";");
        shell.setCenter(pageScroller);

        journal = new JournalEntrySection(this, currentUser);
        ledger = new LedgerSection(this, currentUser);
        worksheet = new WorksheetSection(this, currentUser, () -> {
            navigate("journal");
            journal.addAdjustment();
        });
        recent = new RecentViewsSection(this, currentUser, this::openRecent);
        dashboard = new OverviewSection(this, currentUser, this::navigate,
                entry -> {
                    navigate("journal");
                    journal.openRecent(entry.getId());
                }, journal::addNewEntry);
        stage.setScene(new Scene(shell, 1440, 900));
        navigate("dashboard");
    }

    private Node buildToolbar() {
        BorderPane toolbar = new BorderPane();
        toolbar.setPadding(new Insets(14, 24, 14, 24));
        toolbar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent "
                + EntriTheme.BORDER + " transparent;");
        Label brandName = new Label("ENTRI");
        brandName.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.OLIVE + ";");
        HBox brand = new HBox(8, logoImage(34), brandName);
        brand.setAlignment(Pos.CENTER_LEFT);
        pageTitle = new Label("Dashboard");
        pageTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + EntriTheme.TEXT + ";");
        Label avatar = new Label(initials(currentUser.getUsername()));
        avatar.setTooltip(new Tooltip("Account menu"));
        avatar.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 7 10;"
                + " -fx-background-radius: 18;");
        MenuItem accountItem = new MenuItem("My Account");
        accountItem.setOnAction(e -> showMyAccount());
        currencyMenu = new Menu("Currency display: " + currencySymbol());
        MenuItem phpItem = new MenuItem("PHP — Philippine Peso (\u20b1)");
        phpItem.setOnAction(e -> changeCurrency("PHP"));
        MenuItem usdItem = new MenuItem("USD — US Dollar ($)");
        usdItem.setOnAction(e -> changeCurrency("USD"));
        MenuItem displayNote = new MenuItem("Display only; amounts are not converted");
        displayNote.setDisable(true);
        currencyMenu.getItems().addAll(phpItem, usdItem, new SeparatorMenuItem(), displayNote);
        MenuItem developersItem = new MenuItem("Developers");
        developersItem.setOnAction(e -> showDevelopers());
        MenuItem logoutItem = new MenuItem("Log Out");
        logoutItem.setOnAction(e -> logout());
        MenuButton accountMenu = new MenuButton(currentUser.getUsername(), avatar);
        accountMenu.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.TEXT + ";");
        accountMenu.getItems().addAll(accountItem, currencyMenu, developersItem,
                new SeparatorMenuItem(), logoutItem);
        HBox userControls = new HBox(9, accountMenu);
        userControls.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setLeft(brand);
        toolbar.setCenter(pageTitle);
        toolbar.setRight(userControls);
        return toolbar;
    }

    private void loadCurrencyPreference(long userId) {
        async(() -> settings.getCurrency(userId), savedCurrency -> {
            if (currentUser == null || currentUser.getId() != userId) return;
            if (CurrencyFormatter.isSupported(savedCurrency)) {
                currencyCode = savedCurrency;
            }
            showDashboard();
        }, error -> {
            if (currentUser == null || currentUser.getId() != userId) return;
            showDashboard();
            showError("Could not load currency preference",
                    safeMessage(error) + "\n\nRun database\\entri_schema.sql in MySQL Workbench "
                            + "to add the user settings table.");
        });
    }

    private void changeCurrency(String newCurrency) {
        if (currentUser == null || !CurrencyFormatter.isSupported(newCurrency)
                || newCurrency.equals(currencyCode)) {
            return;
        }

        String oldCurrency = currencyCode;
        long userId = currentUser.getId();
        Menu selectedMenu = currencyMenu;
        currencyCode = newCurrency;
        updateCurrencyMenu();
        refreshCurrencyDisplays();
        selectedMenu.setDisable(true);

        async(() -> {
            settings.saveCurrency(userId, newCurrency);
            return null;
        }, ignored -> {
            if (currencyMenu == selectedMenu) {
                currencyMenu.setDisable(false);
            }
        }, error -> {
            if (currencyMenu == selectedMenu) {
                currencyMenu.setDisable(false);
            }
            if (currentUser != null && currentUser.getId() == userId) {
                currencyCode = oldCurrency;
                updateCurrencyMenu();
                refreshCurrencyDisplays();
            }
            showError("Could not save currency preference",
                    safeMessage(error) + "\n\nRun database\\entri_schema.sql in MySQL Workbench "
                            + "to add the user settings table.");
        });
    }

    private void updateCurrencyMenu() {
        if (currencyMenu != null) {
            currencyMenu.setText("Currency display: " + currencySymbol());
        }
    }

    private void refreshCurrencyDisplays() {
        if (journal != null) journal.reload();
        if (ledger != null) ledger.reload();
        if (worksheet != null) worksheet.reload();
        if (dashboard != null) dashboard.reload();
    }

    private VBox buildSidebar() {
        VBox menu = new VBox(8);
        menu.setPadding(new Insets(20, 12, 14, 12));
        menu.setPrefWidth(218);
        menu.setStyle("-fx-background-color: " + EntriTheme.OLIVE + ";");
        Label logoName = new Label("Entri");
        logoName.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.CORNSILK + ";");
        HBox logo = new HBox(10, logoImage(42), logoName);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.setPadding(new Insets(0, 0, 18, 12));
        menu.getChildren().add(logo);
        VBox primary = new VBox(6);
        primary.getChildren().addAll(
                navButton("Dashboard", "dashboard"),
                navButton("Journal Entry", "journal"),
                navButton("Ledger", "ledger"),
                navButton("12-Column Worksheet", "worksheet"),
                navButton("Recent Views", "recent"));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox bottom = new VBox(6, navButton("My Account", "account"),
                navButton("Developers", "developers"), navButton("Log Out", "logout"));
        menu.getChildren().addAll(primary, spacer, new Separator(), bottom);
        return menu;
    }

    private Button navButton(String text, String target) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.CORNSILK
                + "; -fx-font-weight: bold; -fx-padding: 12 10; -fx-background-radius: 7;");
        button.setOnAction(e -> {
            if ("account".equals(target)) {
                showMyAccount();
            } else if ("developers".equals(target)) {
                showDevelopers();
            } else if ("logout".equals(target)) {
                logout();
            } else {
                navigate(target);
            }
        });
        button.setUserData(target);
        return button;
    }

    private void navigate(String target) {
        if (currentUser == null || shell == null) { showLogin(false, null); return; }
        Node section;
        if ("journal".equals(target)) {
            section = journal;
            pageTitle.setText("Journal Entry");
        } else if ("ledger".equals(target)) {
            section = ledger;
            pageTitle.setText("General Ledger");
        } else if ("worksheet".equals(target)) {
            section = worksheet;
            pageTitle.setText("12-Column Worksheet");
        } else if ("recent".equals(target)) {
            section = recent;
            pageTitle.setText("Recent Views");
        } else {
            section = dashboard;
            pageTitle.setText("Dashboard");
            target = "dashboard";
        }
        pageScroller.setContent(section);
        for (Node node : sidebar.lookupAll(".button")) {
            if (node instanceof Button) {
                Button button = (Button) node;
                boolean active = target.equals(button.getUserData());
                button.setStyle("-fx-background-color: " + (active ? EntriTheme.IVORY : "transparent")
                        + "; -fx-text-fill: " + (active ? EntriTheme.TEXT : EntriTheme.CORNSILK)
                        + "; -fx-font-weight: bold; -fx-padding: 12 10; -fx-background-radius: 7;");
            }
        }
        if (section instanceof AccountingSection) {
            ((AccountingSection) section).reload();
        }
        if ("worksheet".equals(target)) recordRecent("WORKSHEET", 0, "12-Column Worksheet");
    }

    void openRecent(RecentView view) {
        if (currentUser == null) return;
        if ("JOURNAL_ENTRY".equals(view.getType())) {
            navigate("journal");
            journal.openRecent(view.getItemId());
        } else if ("LEDGER_ACCOUNT".equals(view.getType())) {
            navigate("ledger");
            ledger.openAccount(view.getItemId());
        } else if ("WORKSHEET".equals(view.getType())) {
            navigate("worksheet");
        } else {
            showError("Recent Views", "This item type is not available.");
        }
    }

    private void recordRecent(String type, long id, String name) {
        User activeUser = currentUser;
        if (activeUser == null) return;
        async(() -> { accounting.recordRecent(activeUser.getId(), type, id, name); return null; },
                ignored -> {}, ex -> showError("Could not save recent view", safeMessage(ex)));
    }

    private void showMyAccount() {
        if (currentUser == null) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("My Account");
        dialog.setHeaderText("Account information");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Label username = new Label("Username: " + currentUser.getUsername());
        Label email = new Label("Email: " + currentUser.getEmail());
        Label identifier = new Label("Account ID: " + currentUser.getId());
        Label created = new Label(currentUser.getCreatedAt() == null ? "Creation date unavailable."
                : "Created: " + currentUser.getCreatedAt().toLocalDate());
        Label privacy = new Label("Password information is never displayed here.");
        EntriTheme.muted(privacy);
        TextField emailField = new TextField(currentUser.getEmail());
        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText("Current password to confirm email change");
        Label updateStatus = new Label();
        updateStatus.setWrapText(true);
        Button updateEmail = new Button("Update Email");
        EntriTheme.primary(updateEmail);
        updateEmail.setOnAction(e -> {
            char[] password = currentPassword.getText().toCharArray();
            currentPassword.clear();
            updateEmail.setDisable(true);
            updateStatus.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + ";");
            updateStatus.setText("Updating email...");
            User account = currentUser;
            async(() -> auth.updateEmail(account, emailField.getText(), password), updated -> {
                java.util.Arrays.fill(password, '\0');
                currentUser = updated;
                email.setText("Email: " + updated.getEmail());
                updateEmail.setDisable(false);
                updateStatus.setStyle("-fx-text-fill: " + EntriTheme.SUCCESS + ";");
                updateStatus.setText("Email updated successfully.");
            }, ex -> {
                java.util.Arrays.fill(password, '\0');
                updateEmail.setDisable(false);
                updateStatus.setStyle("-fx-text-fill: " + EntriTheme.ERROR + ";");
                updateStatus.setText(safeMessage(ex));
            });
        });
        VBox content = new VBox(12, username, email, identifier, created,
                new Separator(), new Label("Change email address"), emailField,
                currentPassword, updateEmail, updateStatus, privacy);
        content.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showDevelopers() {
        if (currentUser == null) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Developers");
        dialog.setHeaderText("Meet the Developers");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox cards = new VBox(12);
        cards.setPadding(new Insets(12));
        for (String item : DeveloperInfo.TEAM) {
            Label person = new Label(item);
            person.setWrapText(true);
            person.setMaxWidth(420);
            person.setPadding(new Insets(16));
            person.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-background-radius: 10;"
                    + " -fx-border-color: " + EntriTheme.BORDER + "; -fx-border-radius: 10;");
            cards.getChildren().add(person);
        }
        dialog.getDialogPane().setContent(cards);
        dialog.showAndWait();
    }

    void logout() {
        if (currentUser == null) { showLogin(false, null); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Your accounting records will remain saved. Log out now?",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Log Out");
        confirm.setHeaderText("Log out of Entri?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            currentUser = null;
            currencyCode = "USD";
            currencyMenu = null;
            shell = null;
            dashboard = null;
            journal = null;
            ledger = null;
            worksheet = null;
            recent = null;
            showLogin(false, null);
        }
    }

    private static String initials(String username) {
        if (username == null || username.isBlank()) return "E";
        return username.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    @Override public void stop() { currentUser = null; }

    public static void main(String[] args) { launch(args); }
}
