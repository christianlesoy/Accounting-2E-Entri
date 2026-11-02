package main.java.com.entri;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;
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
    private VBox sidebar;
    private Button sidebarToggle;
    private OverviewSection dashboard;
    private JournalEntrySection journal;
    private LedgerSection ledger;
    private AccountsSection accounts;
    private WorksheetSection worksheet;
    private RecentViewsSection recent;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        URL logoResource = MainApp.class.getResource("/images/logo.png");
        if (logoResource == null) throw new IllegalStateException("Missing app logo: /images/logo.png");
        appLogo = new Image(logoResource.toExternalForm());
        stage.getIcons().add(appLogo);
        stage.setTitle("Entri — Accounting Management System");
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        if (getParameters().getRaw().contains("--preview")) {
            showPreview();
        } else {
            showInitializing();
        }
        stage.show();
        if (!getParameters().getRaw().contains("--preview")) {
            async(() -> {
                Database.initialize();
                return null;
            }, ignored -> showLogin(false, null), error -> {
                showError("Database initialization failed", safeMessage(error)
                        + "\n\nEntri could not verify its database connection. The login screen will not be opened.");
                stage.close();
                Platform.exit();
            });
        }
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

    private void showInitializing() {
        Label status = new Label("Checking database connection...");
        EntriTheme.muted(status);
        VBox content = new VBox(14, new ProgressIndicator(), status);
        content.setAlignment(Pos.CENTER);
        content.setStyle(EntriTheme.BASE);
        stage.setScene(new Scene(content, 1100, 760));
    }

    private void showLogin(boolean registration, String feedback) {
        Label brandName = new Label("Entri");
        brandName.setStyle("-fx-font-size: 25px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.OLIVE + ";");
        Label brandCaption = new Label("ACCOUNTING WORKSPACE");
        brandCaption.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.SECONDARY + ";");
        VBox brandText = new VBox(1, brandName, brandCaption);
        HBox brand = new HBox(12, logoImage(48), brandText);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(registration ? "Create your account" : "Welcome back");
        title.setFont(Font.font("Georgia", 30));
        title.setStyle("-fx-text-fill: " + EntriTheme.TEXT + ";");
        Label subtitle = new Label(registration ? "Set up your accounting workspace."
                : "Sign in to manage your accounting records.");
        subtitle.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + "; -fx-font-size: 14px;");
        subtitle.setWrapText(true);

        TextField username = new TextField();
        username.setPromptText(registration ? "Choose a username" : "Username or email");
        username.setAccessibleText(registration ? "Username" : "Username or email");
        TextField email = new TextField();
        email.setPromptText("Email address");
        email.setAccessibleText("Email address");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setAccessibleText("Password");
        TextField visiblePassword = new TextField();
        visiblePassword.setPromptText("Password");
        visiblePassword.setAccessibleText("Password");
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        visiblePassword.setVisible(false);
        visiblePassword.setManaged(false);
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText("Confirm password");
        confirmation.setAccessibleText("Confirm password");
        TextField visibleConfirmation = new TextField();
        visibleConfirmation.setPromptText("Confirm password");
        visibleConfirmation.setAccessibleText("Confirm password");
        visibleConfirmation.textProperty().bindBidirectional(confirmation.textProperty());
        visibleConfirmation.setVisible(false);
        visibleConfirmation.setManaged(false);
        for (TextField field : List.of(username, email, password, visiblePassword,
                confirmation, visibleConfirmation)) {
            styleAuthenticationInput(field);
        }

        CheckBox showPassword = new CheckBox("Show");
        showPassword.setAccessibleText("Show or hide password");
        showPassword.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + ";");
        showPassword.selectedProperty().addListener((observable, wasVisible, isVisible) -> {
            password.setVisible(!isVisible);
            password.setManaged(!isVisible);
            visiblePassword.setVisible(isVisible);
            visiblePassword.setManaged(isVisible);
            confirmation.setVisible(!isVisible);
            confirmation.setManaged(!isVisible);
            visibleConfirmation.setVisible(isVisible);
            visibleConfirmation.setManaged(isVisible);
        });
        StackPane passwordInput = new StackPane(password, visiblePassword);
        HBox.setHgrow(passwordInput, Priority.ALWAYS);
        HBox passwordRow = new HBox(12, passwordInput, showPassword);
        passwordRow.setAlignment(Pos.CENTER_LEFT);
        StackPane confirmationInput = new StackPane(confirmation, visibleConfirmation);

        VBox fields = new VBox(9, authenticationField("Username", username));
        if (registration) fields.getChildren().add(authenticationField("Email address", email));
        fields.getChildren().add(authenticationField("Password", passwordRow));
        if (registration) {
            Label passwordHint = new Label("Use at least 10 characters for your password.");
            passwordHint.setStyle("-fx-text-fill: " + EntriTheme.SECONDARY + "; -fx-font-size: 12px;");
            fields.getChildren().addAll(passwordHint,
                    authenticationField("Confirm password", confirmationInput));
        }

        Label message = new Label(feedback == null ? " " : feedback);
        message.setWrapText(true);
        message.setMinHeight(24);
        message.setStyle("-fx-text-fill: " + (feedback != null && feedback.startsWith("Account created")
                ? EntriTheme.SUCCESS : feedback == null ? EntriTheme.SECONDARY : EntriTheme.ERROR) + ";");
        Button submit = new Button(registration ? "Create account" : "Log In");
        Button alternate = new Button(registration
                ? "Already have an account?  Log in"
                : "Don't have an account?  Create one");
        Button clear = new Button("Clear");
        Button preview = new Button("Explore a sample preview");
        EntriTheme.primary(submit);
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setPrefHeight(48);
        alternate.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.OLIVE
                + "; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 7 4;");
        clear.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.SECONDARY
                + "; -fx-cursor: hand; -fx-padding: 7 4;");
        preview.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.OLIVE
                + "; -fx-cursor: hand; -fx-padding: 7 4;");
        clear.setVisible(!registration);
        clear.setManaged(!registration);
        preview.setVisible(!registration);
        preview.setManaged(!registration);
        clear.setOnAction(e -> {
            username.clear();
            password.clear();
            message.setText(" ");
            username.requestFocus();
        });
        preview.setOnAction(e -> showPreview());
        submit.setDefaultButton(true);
        alternate.setOnAction(e -> showLogin(!registration, null));

        HBox secondaryActions = new HBox(12, clear, preview);
        secondaryActions.setAlignment(Pos.CENTER);
        VBox form = new VBox(13, brand, title, subtitle, fields, message,
                submit, secondaryActions, alternate);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(32, 38, 32, 38));
        form.setMinWidth(330);
        HBox.setHgrow(form, Priority.ALWAYS);
        HBox card = new HBox(12, form, authenticationVisual());
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(14));
        card.setMaxWidth(1040);
        card.setMaxHeight(680);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 30;"
                + " -fx-border-color: rgba(255,255,255,0.68); -fx-border-radius: 30;"
                + " -fx-effect: dropshadow(gaussian, rgba(28,38,18,0.22), 34, 0.18, 0, 14);");
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #8A9A5B 0%, "
                + EntriTheme.OLIVE + " 48%, #2B3318 100%);");
        List<Control> authenticationControls = List.of(username, email, password, visiblePassword,
                confirmation, visibleConfirmation, showPassword, submit, clear, alternate, preview);
        submit.setOnAction(e -> {
            if (!registration && (username.getText() == null || username.getText().isBlank()
                    || password.getText() == null || password.getText().isEmpty())) {
                message.setStyle("-fx-text-fill: " + EntriTheme.ERROR + ";");
                message.setText("Enter your username and password.");
                if (username.getText() == null || username.getText().isBlank()) username.requestFocus();
                else (showPassword.isSelected() ? visiblePassword : password).requestFocus();
                return;
            }
            submit.setDisable(true);
            authenticationControls.forEach(control -> control.setDisable(true));
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
                            authenticationControls.forEach(control -> control.setDisable(false));
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
                    authenticationControls.forEach(control -> control.setDisable(false));
                    if (ex instanceof IllegalArgumentException) {
                        message.setStyle("-fx-text-fill: " + EntriTheme.ERROR + ";");
                        message.setText("Invalid username/email or password.");
                    } else {
                        message.setText("");
                        showError("Could not sign in", safeMessage(ex));
                    }
                });
            }
        });
        username.setOnAction(e -> {
            if (registration) email.requestFocus();
            else submit.fire();
        });
        email.setOnAction(e -> password.requestFocus());
        password.setOnAction(e -> {
            if (registration) confirmation.requestFocus();
            else submit.fire();
        });
        visiblePassword.setOnAction(e -> {
            if (registration) visibleConfirmation.requestFocus();
            else submit.fire();
        });
        confirmation.setOnAction(e -> submit.fire());
        visibleConfirmation.setOnAction(e -> submit.fire());
        stage.setScene(new Scene(root, 1100, 760));
    }

    private static VBox authenticationField(String labelText, Node input) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.OLIVE + ";");
        VBox field = new VBox(6, label, input);
        if (input instanceof Region) ((Region) input).setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static void styleAuthenticationInput(TextField input) {
        input.setPrefHeight(46);
        input.setStyle("-fx-background-color: #FFFEFA; -fx-background-radius: 12;"
                + " -fx-border-color: " + EntriTheme.BORDER + "; -fx-border-radius: 12;"
                + " -fx-padding: 0 14; -fx-text-fill: " + EntriTheme.TEXT
                + "; -fx-prompt-text-fill: #929581;");
    }

    private Node authenticationVisual() {
        Label eyebrow = new Label("YOUR FINANCES, IN FOCUS");
        eyebrow.setStyle("-fx-text-fill: " + EntriTheme.IVORY
                + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label headline = new Label("Keep every entry\nin its place.");
        headline.setFont(Font.font("Georgia", 31));
        headline.setStyle("-fx-text-fill: " + EntriTheme.CORNSILK + ";");
        headline.setWrapText(true);
        Label description = new Label("Your accounts, journal, and reports —\nconnected in one calm workspace.");
        description.setStyle("-fx-text-fill: " + EntriTheme.IVORY + "; -fx-font-size: 14px;");
        description.setWrapText(true);
        VBox reportCard = new VBox(15, visualRow("ACCOUNTS", 0.88),
                visualRow("GENERAL JOURNAL", 0.70),
                visualRow("FINANCIAL REPORTS", 0.80));
        reportCard.setPadding(new Insets(20));
        reportCard.setStyle("-fx-background-color: rgba(254,250,224,0.12);"
                + " -fx-background-radius: 18; -fx-border-color: rgba(254,250,224,0.24);"
                + " -fx-border-radius: 18;");
        Label footer = new Label("A clearer picture of your books.");
        footer.setStyle("-fx-text-fill: " + EntriTheme.CORNSILK
                + "; -fx-font-size: 13px; -fx-font-weight: bold;");
        VBox content = new VBox(22, eyebrow, headline, description, reportCard, footer);
        content.setAlignment(Pos.BOTTOM_LEFT);
        content.setPadding(new Insets(32));

        Circle largeRing = new Circle(145);
        largeRing.setFill(javafx.scene.paint.Color.TRANSPARENT);
        largeRing.setStroke(javafx.scene.paint.Color.rgb(254, 250, 224, 0.13));
        Circle smallRing = new Circle(105);
        smallRing.setFill(javafx.scene.paint.Color.TRANSPARENT);
        smallRing.setStroke(javafx.scene.paint.Color.rgb(254, 250, 224, 0.10));
        StackPane panel = new StackPane(largeRing, smallRing, content);
        StackPane.setAlignment(largeRing, Pos.TOP_RIGHT);
        StackPane.setMargin(largeRing, new Insets(-58, -66, 0, 0));
        StackPane.setAlignment(smallRing, Pos.TOP_RIGHT);
        StackPane.setMargin(smallRing, new Insets(-18, -26, 0, 0));
        StackPane.setAlignment(content, Pos.BOTTOM_LEFT);
        panel.setMinWidth(260);
        panel.setPrefWidth(420);
        panel.setMaxWidth(460);
        panel.setMaxHeight(Double.MAX_VALUE);
        panel.setStyle("-fx-background-color: linear-gradient(to bottom right, #738248, "
                + EntriTheme.OLIVE + " 52%, #3A4522); -fx-background-radius: 23;");
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Node visualRow(String title, double barWidth) {
        Label marker = new Label();
        marker.setMinSize(8, 8);
        marker.setMaxSize(8, 8);
        marker.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-background-radius: 4;");
        Label text = new Label(title);
        text.setStyle("-fx-text-fill: " + EntriTheme.CORNSILK
                + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        Region bar = new Region();
        bar.setPrefHeight(3);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-background-color: rgba(254,250,224,0.60); -fx-background-radius: 2;");
        HBox labelRow = new HBox(9, marker, text);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        VBox row = new VBox(9, labelRow, bar);
        row.setMaxWidth(Double.MAX_VALUE);
        bar.prefWidthProperty().bind(row.widthProperty().multiply(barWidth));
        return row;
    }

    private void showPreview() {
        BorderPane previewShell = new BorderPane();
        previewShell.setStyle(EntriTheme.BASE);

        BorderPane toolbar = new BorderPane();
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setStyle("-fx-background-color: " + EntriTheme.OLIVE + ";");
        Label previewBadge = new Label("SAMPLE PREVIEW");
        previewBadge.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 7 10; -fx-background-radius: 12;");
        VBox navigation = new VBox(8);
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
            page.setCenter(previewPage("developers"));
        });
        MenuItem logoutItem = new MenuItem("Back to Login");
        logoutItem.setStyle("-fx-text-fill: #DC2626;");
        logoutItem.setOnAction(event -> showLogin(false, null));
        MenuButton accountMenu = new MenuButton("Alex Morgan", avatar);
        accountMenu.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold;");
        accountMenu.getItems().addAll(profileItem, settingsItem, developersItem,
                new SeparatorMenuItem(), logoutItem);
        Button previewNewEntry = new Button("+  New Journal Entry");
        previewNewEntry.setStyle("-fx-background-color: " + EntriTheme.HOVER_OLIVE + "; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 10 16;");
        previewNewEntry.setOnAction(event -> {
            page.setCenter(previewPage("journal"));
        });
        Button menuButton = new Button("\u2630");
        menuButton.setAccessibleText("Toggle navigation menu");
        menuButton.setTooltip(new Tooltip("Toggle navigation menu"));
        menuButton.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.CORNSILK
                + "; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 4 10;");
        Label previewBrand = new Label("ENTRI");
        previewBrand.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.CORNSILK + ";");
        HBox brand = new HBox(10, menuButton, logoImage(32), previewBrand);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMinWidth(0);
        Button homeButton = new Button("Home");
        EntriTheme.secondary(homeButton);
        homeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold; -fx-padding: 10 12;");
        homeButton.setOnAction(event -> {
            page.setCenter(previewPage("dashboard"));
            updatePreviewNavigation(navigation, "dashboard");
        });
        HBox centerActions = new HBox(10, homeButton, previewNewEntry, previewBadge);
        centerActions.setAlignment(Pos.CENTER);
        accountMenu.setMinWidth(0);
        BorderPane.setAlignment(brand, Pos.CENTER_LEFT);
        BorderPane.setAlignment(centerActions, Pos.CENTER);
        BorderPane.setAlignment(accountMenu, Pos.CENTER_RIGHT);
        toolbar.setLeft(brand);
        toolbar.setCenter(centerActions);
        toolbar.setRight(accountMenu);
        toolbar.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            boolean compact = newWidth.doubleValue() < 900;
            previewNewEntry.setText(compact ? "New Entry" : "+  New Journal Entry");
            previewBadge.setText(compact ? "PREVIEW" : "SAMPLE PREVIEW");
            if (newWidth.doubleValue() < 900) accountMenu.setText("Account");
            else accountMenu.setText("Alex Morgan");
            centerActions.setSpacing(compact ? 6 : 10);
            brand.setSpacing(compact ? 7 : 10);
        });
        previewShell.setTop(toolbar);

        VBox sidebar = new VBox(8);
        sidebar.setPadding(new Insets(22, 16, 18, 16));
        sidebar.setMinWidth(170);
        sidebar.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(170, Math.min(260, stage.getWidth() * 0.2)),
                stage.widthProperty()));
        sidebar.setMaxWidth(280);
        sidebar.setStyle("-fx-background-color: " + EntriTheme.CORNSILK + ";"
                + " -fx-border-color: transparent " + EntriTheme.BORDER + " transparent transparent;");
        Label profileName = new Label("Alex Morgan");
        profileName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.OLIVE + ";");
        Label sidebarAvatar = new Label("AM");
        sidebarAvatar.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 11 12;"
                + " -fx-background-radius: 24;");
        HBox profile = new HBox(12, sidebarAvatar, profileName);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.setPadding(new Insets(2, 8, 18, 8));
        Label menuLabel = new Label("MENU");
        menuLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.SECONDARY + "; -fx-padding: 14 8 8 8;");
        for (String[] item : new String[][] {
                {"\u2302    Home", "dashboard"}, {"Journal Entry", "journal"}, {"Ledger", "ledger"},
                {"12-Column Worksheet", "worksheet"}, {"Recent Views", "recent"}, {"Developers", "developers"}
        }) {
            Button button = new Button(item[0]);
            button.setUserData(item[1]);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(44);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                    + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 10 12;"
                    + " -fx-background-radius: 7;");
            button.setOnAction(event -> {
                page.setCenter(previewPage(item[1]));
                updatePreviewNavigation(navigation, item[1]);
            });
            navigation.getChildren().add(button);
        }
        sidebar.getChildren().addAll(profile, menuLabel, navigation, new Separator());
        sidebar.setVisible(false);
        sidebar.setManaged(false);
        menuButton.setOnAction(event -> {
            boolean show = !sidebar.isVisible();
            sidebar.setVisible(show);
            sidebar.setManaged(show);
        });
        previewShell.setLeft(sidebar);
        ScrollPane scroller = new ScrollPane(page);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        scroller.setStyle("-fx-background: " + EntriTheme.CORNSILK + "; -fx-background-color: "
                + EntriTheme.CORNSILK + ";");
        previewShell.setCenter(scroller);
        Button dashboardButton = null;
        for (Node node : navigation.getChildren()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                if (button.getText().endsWith("Home")) {
                    dashboardButton = button;
                    break;
                }
            }
        }
        if (dashboardButton == null) {
            throw new IllegalStateException("Dashboard navigation button is missing.");
        }
        dashboardButton.fire();
        Scene scene = new Scene(previewShell, 1100, 760);
        URL styleResource = MainApp.class.getResource("/styles/entri.css");
        if (styleResource == null) throw new IllegalStateException("Missing app styles: /styles/entri.css");
        scene.getStylesheets().add(styleResource.toExternalForm());
        stage.setScene(scene);
    }

    private static void updatePreviewNavigation(VBox navigation, String target) {
        for (Node node : navigation.getChildren()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                boolean active = target.equals(button.getUserData());
                button.setStyle("-fx-background-color: "
                        + (active ? EntriTheme.IVORY : "transparent") + "; -fx-text-fill: "
                        + EntriTheme.OLIVE
                        + "; -fx-font-weight: bold; -fx-padding: 10 12; -fx-background-radius: 7;");
            }
        }
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
            Label workspaceLabel = new Label("YOUR WORKSPACE");
            workspaceLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                    + EntriTheme.SECONDARY + ";");
            Label welcomeTitle = new Label("Welcome back, Alex Morgan!");
            welcomeTitle.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: "
                    + EntriTheme.OLIVE + ";");
            Label welcomeText = new Label("Your accounting workspace is ready.");
            welcomeText.setStyle("-fx-font-size: 15px; -fx-text-fill: " + EntriTheme.TEXT + ";");
            VBox welcome = new VBox(9, workspaceLabel, welcomeTitle, welcomeText);
            welcome.setPadding(new Insets(26, 28, 26, 28));
            welcome.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-background-radius: 18;"
                    + " -fx-border-color: " + EntriTheme.BORDER + "; -fx-border-radius: 18;");
            content.getChildren().addAll(welcome,
                    previewHeading("Accounting Dashboard", "A summary of your accounting workspace."));
            GridPane financialGrid = new GridPane();
            financialGrid.setHgap(18);
            financialGrid.setVgap(18);
            VBox worksheetCard = previewCard("12 Columns Worksheet", previewWorksheetTable());
            VBox recentCard = previewCard("Recent Views", previewRecentTable());
            List<Node> dashboardCards = java.util.Arrays.asList(
                    previewCard("Journal Entry", previewJournalTable()),
                    previewCard("Ledger", previewLedgerTable()), worksheetCard, recentCard);
            financialGrid.widthProperty().addListener((observable, oldWidth, newWidth) ->
                    arrangePreviewCards(financialGrid, dashboardCards, newWidth.doubleValue() >= 900 ? 2 : 1));
            arrangePreviewCards(financialGrid, dashboardCards, 2);
            VBox.setVgrow(financialGrid, Priority.ALWAYS);
            content.getChildren().add(financialGrid);
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
        EntriTheme.table(table);
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
        table.setMinWidth(1290);
        table.setPrefHeight(280);
        ScrollPane horizontalScroll = new ScrollPane(table);
        horizontalScroll.setFitToHeight(true);
        horizontalScroll.setPannable(true);
        horizontalScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        horizontalScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        horizontalScroll.setMinHeight(180);
        horizontalScroll.setPrefViewportHeight(280);
        table.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(1290, horizontalScroll.getViewportBounds().getWidth()),
                horizontalScroll.viewportBoundsProperty()));
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
        table.setMinHeight(150);
        table.setPrefHeight(220);
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
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(260);
        if (body instanceof Region) {
            ((Region) body).setMinHeight(150);
            VBox.setVgrow(body, Priority.ALWAYS);
        }
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: "
                + EntriTheme.BORDER + "; -fx-border-radius: 10;");
        return card;
    }

    private static void arrangePreviewCards(GridPane grid, List<Node> cards, int columns) {
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
                Node card = cards.get(row);
                GridPane.setHgrow(card, Priority.ALWAYS);
                GridPane.setVgrow(card, Priority.ALWAYS);
                grid.add(card, 0, row);
            }
        } else {
            for (int column = 0; column < 2; column++) {
                Node card = cards.get(column);
                GridPane.setHgrow(card, Priority.ALWAYS);
                GridPane.setVgrow(card, Priority.ALWAYS);
                grid.add(card, column, 0);
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

    private TableView<String[]> previewTable(String[] headers, String[][] rows) {
        TableView<String[]> table = new TableView<>();
        EntriTheme.table(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int index = 0; index < headers.length; index++) {
            table.getColumns().add(previewColumn(headers[index], index));
        }
        table.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        table.setRowFactory(view -> previewTableRow(rows.length));
        table.setMinHeight(150);
        table.setPrefHeight(220);
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
                    ? "-fx-background-color: " + EntriTheme.IVORY + "; -fx-font-weight: bold; -fx-border-color: "
                            + EntriTheme.BORDER + " transparent transparent transparent;"
                    : row.getIndex() % 2 == 0
                            ? "-fx-background-color: white;"
                            : "-fx-background-color: #FFFDF5;");
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
        sidebar.setVisible(false);
        sidebar.setManaged(false);
        shell.setLeft(sidebar);
        pageScroller = new ScrollPane();
        pageScroller.setFitToWidth(true);
        pageScroller.setFitToHeight(true);
        pageScroller.setStyle("-fx-background: " + EntriTheme.CORNSILK + "; -fx-background-color: "
                + EntriTheme.CORNSILK + ";");
        shell.setCenter(pageScroller);

        journal = new JournalEntrySection(this, currentUser);
        ledger = new LedgerSection(this, currentUser);
        accounts = new AccountsSection(this, currentUser, ledger);
        worksheet = new WorksheetSection(this, currentUser, () -> {
            navigate("journal");
            journal.addAdjustment();
        }, ledger::addNewAccount, ledger::editAccountDialog, ledger::removeAccountDialog);
        recent = new RecentViewsSection(this, currentUser, this::openRecent);
        dashboard = new OverviewSection(this, currentUser, this::navigate,
                entry -> {
                    navigate("journal");
                    journal.openRecent(entry.getId());
                }, journal::addNewEntry);
        Scene scene = new Scene(shell, 1100, 760);
        URL styleResource = MainApp.class.getResource("/styles/entri.css");
        if (styleResource == null) throw new IllegalStateException("Missing app styles: /styles/entri.css");
        scene.getStylesheets().add(styleResource.toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleDashboardShortcut);
        stage.setScene(scene);
        navigate("accounts");
    }

    private void handleDashboardShortcut(KeyEvent event) {
        if (new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN).match(event)) {
            navigate("journal");
            journal.addNewEntry();
            event.consume();
        } else if (new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN).match(event)) {
            Node activePage = pageScroller.getContent();
            if (activePage == journal) {
                journal.focusSearch();
            } else if (activePage == ledger) {
                ledger.focusSearch();
            } else if (activePage == recent) {
                recent.focusSearch();
            } else if (activePage == dashboard) {
                navigate("journal");
                journal.focusSearch();
            }
            event.consume();
        }
    }

    private Node buildToolbar() {
        BorderPane toolbar = new BorderPane();
        toolbar.setPadding(new Insets(10, 18, 10, 18));
        toolbar.setStyle("-fx-background-color: " + EntriTheme.OLIVE + ";");
        Label brandName = new Label("ENTRI");
        brandName.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.CORNSILK + ";");
        sidebarToggle = new Button("\u2630");
        sidebarToggle.setAccessibleText("Toggle navigation menu");
        sidebarToggle.setTooltip(new Tooltip("Toggle navigation menu"));
        sidebarToggle.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.CORNSILK
                + "; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 4 10;");
        sidebarToggle.setOnAction(event -> toggleSidebar());
        HBox brand = new HBox(12, sidebarToggle, logoImage(34), brandName);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label avatar = new Label(initials(currentUser.getUsername()));
        avatar.setTooltip(new Tooltip("Account menu"));
        avatar.setStyle("-fx-background-color: " + EntriTheme.CORNSILK + "; -fx-text-fill: "
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
        accountMenu.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold;");
        accountMenu.getItems().addAll(accountItem, currencyMenu, developersItem,
                new SeparatorMenuItem(), logoutItem);
        accountMenu.setMinWidth(0);
        accountMenu.setMinHeight(40);
        Button newEntry = new Button("+  New Journal Entry");
        EntriTheme.primary(newEntry);
        newEntry.setStyle("-fx-background-color: " + EntriTheme.HOVER_OLIVE + "; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold; -fx-background-radius: 7; -fx-padding: 10 16;");
        newEntry.setOnAction(event -> {
            navigate("journal");
            journal.addNewEntry();
        });
        newEntry.setMinHeight(40);
        Button homeButton = new Button("Home");
        homeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                + EntriTheme.CORNSILK + "; -fx-font-weight: bold; -fx-padding: 10 12;");
        homeButton.setMinHeight(40);
        homeButton.setOnAction(event -> navigate("dashboard"));
        HBox centerActions = new HBox(10, homeButton, newEntry);
        centerActions.setAlignment(Pos.CENTER);
        brand.setMinHeight(40);
        sidebarToggle.setMinHeight(40);
        BorderPane.setAlignment(brand, Pos.CENTER_LEFT);
        BorderPane.setAlignment(centerActions, Pos.CENTER);
        BorderPane.setAlignment(accountMenu, Pos.CENTER_RIGHT);
        toolbar.setLeft(brand);
        toolbar.setCenter(centerActions);
        toolbar.setRight(accountMenu);
        toolbar.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            double width = newWidth.doubleValue();
            boolean compact = width < 900;
            newEntry.setText(compact ? "New Entry" : "+  New Journal Entry");
            accountMenu.setText(width < 900 ? "Account" : currentUser.getUsername());
            centerActions.setSpacing(compact ? 6 : 10);
            brand.setSpacing(compact ? 7 : 10);
        });
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
                    safeMessage(error) + "\n\nRun database\\entri_schema.sql in the Supabase SQL Editor "
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
                    safeMessage(error) + "\n\nRun database\\entri_schema.sql in the Supabase SQL Editor "
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

    void refreshAccountingViews() {
        if (journal != null) journal.reload();
        if (ledger != null) ledger.reload();
        if (accounts != null) accounts.reload();
        if (worksheet != null) worksheet.reload();
        if (dashboard != null) dashboard.reload();
    }

    private VBox buildSidebar() {
        VBox menu = new VBox(8);
        menu.setPadding(new Insets(22, 16, 18, 16));
        menu.setMinWidth(170);
        menu.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(170, Math.min(260, stage.getWidth() * 0.2)),
                stage.widthProperty()));
        menu.setMaxWidth(280);
        menu.setStyle("-fx-background-color: " + EntriTheme.CORNSILK + ";"
                + " -fx-border-color: transparent " + EntriTheme.BORDER + " transparent transparent;");
        Label avatar = new Label(initials(currentUser.getUsername()));
        avatar.setStyle("-fx-background-color: " + EntriTheme.IVORY + "; -fx-text-fill: "
                + EntriTheme.OLIVE + "; -fx-font-weight: bold; -fx-padding: 11 14;"
                + " -fx-background-radius: 24;");
        Label username = new Label(currentUser.getUsername());
        username.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.OLIVE + ";");
        HBox profile = new HBox(12, avatar, username);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.setPadding(new Insets(2, 8, 18, 8));
        Label menuLabel = new Label("MENU");
        menuLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: "
                + EntriTheme.SECONDARY + "; -fx-padding: 14 8 8 8;");
        VBox primary = new VBox(8);
        primary.getChildren().addAll(
                navButton("Home", "dashboard"),
                navButton("New Transaction", "newTransaction"),
                navButton("Transactions", "transactions"),
                navButton("Accounts", "accounts"),
                navButton("General Journal", "generalJournal"),
                navButton("General Ledger", "generalLedger"),
                navButton("Balance Sheet", "balanceSheet"),
                navButton("12-Column Worksheet", "worksheet"),
                navButton("Recent Views", "recent"));
        ScrollPane navigation = new ScrollPane(primary);
        navigation.setFitToWidth(true);
        navigation.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navigation.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navigation.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(navigation, Priority.ALWAYS);
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox bottom = new VBox(8, navButton("My Account", "account"),
                navButton("Developers", "developers"));
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: " + EntriTheme.BORDER + ";");
        menu.getChildren().addAll(profile, menuLabel, navigation, spacer, separator, bottom);
        return menu;
    }

    private Button navButton(String text, String target) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(44);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: " + EntriTheme.OLIVE
                + "; -fx-font-weight: bold; -fx-padding: 10 12; -fx-background-radius: 7;");
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

    private void toggleSidebar() {
        if (sidebar == null) return;
        boolean show = !sidebar.isVisible();
        sidebar.setVisible(show);
        sidebar.setManaged(show);
    }

    private void navigate(String target) {
        if (currentUser == null || shell == null) { showLogin(false, null); return; }
        boolean newTransaction = "newTransaction".equals(target);
        if ("journal".equals(target)) target = "generalJournal";
        if ("ledger".equals(target)) target = "generalLedger";
        Node section;
        if ("journal".equals(target) || "transactions".equals(target)
                || "generalJournal".equals(target) || newTransaction) {
            section = journal;
            journal.setSectionHeading(newTransaction ? "Transactions"
                            : "generalJournal".equals(target) ? "General Journal" : "Transactions",
                    "Review posted and unposted transaction records and open their journal details.");
            journal.setGeneralJournalMode("generalJournal".equals(target));
        } else if ("ledger".equals(target) || "generalLedger".equals(target)) {
            section = ledger;
            ledger.setSectionHeading("General Ledger",
                    "Review account activity, running balances, and manage ledger accounts.");
        } else if ("accounts".equals(target)) {
            section = accounts;
        } else if ("worksheet".equals(target) || "balanceSheet".equals(target)) {
            section = worksheet;
            worksheet.setBalanceSheetMode("balanceSheet".equals(target));
        } else if ("recent".equals(target)) {
            section = recent;
        } else {
            section = dashboard;
            target = "dashboard";
        }
        pageScroller.setContent(section);
        for (Node node : sidebar.lookupAll(".button")) {
            if (node instanceof Button) {
                Button button = (Button) node;
                boolean active = target.equals(button.getUserData());
                button.setStyle("-fx-background-color: " + (active ? EntriTheme.IVORY : "transparent")
                        + "; -fx-text-fill: " + EntriTheme.OLIVE
                        + "; -fx-font-weight: bold; -fx-padding: 10 12; -fx-background-radius: 7;");
            }
        }
        if (section instanceof AccountingSection) {
            ((AccountingSection) section).reload();
        }
        if ("worksheet".equals(target)) {
            recordRecent("WORKSHEET", 0, "12-Column Worksheet");
        }
        if ("balanceSheet".equals(target)) {
            recordRecent("WORKSHEET", 0, "Balance Sheet");
        }
        if (newTransaction) journal.addNewEntry();
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
            navigate(view.isBalanceSheetReport() ? "balanceSheet" : "worksheet");
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
            accounts = null;
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
