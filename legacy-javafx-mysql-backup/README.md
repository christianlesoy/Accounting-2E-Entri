# Entri

Entri is a native JavaFX accounting desktop application backed by MySQL. It uses
plain `javac`/`java`; no Maven or Gradle build is required.

## Requirements

- JDK 21
- JavaFX SDK for JDK 21
- MySQL Community Server 8+
- MySQL Connector/J (JDBC driver)

Install JDK 21 from an OpenJDK distribution, download the JavaFX SDK for JDK 21
from [Gluon](https://gluonhq.com/products/javafx/), and download Connector/J
from [MySQL](https://dev.mysql.com/downloads/connector/j/). Extract the JavaFX
SDK to `C:\javafx-sdk-21` and copy the Connector/J jar to
`lib\mysql-connector-j.jar` (create `lib` if needed). These jars remain local
and are excluded from Git.

The launch scripts expect JavaFX at `C:\javafx-sdk-21\lib` and Connector/J at
`lib\mysql-connector-j.jar`. Edit the script variables if you installed them
elsewhere. Keep dependency jars out of source control.

## VS Code Java setup

The project includes `.vscode/settings.json` so the Java extension uses
`src/main/java` as its source root and can find JavaFX and Connector/J.
Install the Java Extension Pack, JDK 21, and JavaFX at
`C:\javafx-sdk-21\lib` as described above. If you install JavaFX elsewhere,
update its path in `.vscode/settings.json`. After opening the project folder,
run **Java: Clean Java Language Server Workspace** from the Command Palette if
old red error markers remain, then reload the window.

## Add the Entri logo

The app logo is stored at `src\main\resources\images\logo.png`. The launcher
copies files in `src\main\resources` into the compiled application. The image
appears on the login screen, navigation sidebar, top toolbar, and application
window icon.

## Database setup

1. Run `database/entri_schema.sql` in MySQL Workbench or with the MySQL client.
   Run it again for an existing Entri database to add the `user_settings` table;
   the script uses `CREATE TABLE IF NOT EXISTS` and does not erase existing data.
2. Uncomment and run the dedicated application-user statements at the end of
   the script as a MySQL administrator. Replace the placeholder password with
   a unique strong password. Do not commit the replaced SQL.
3. Set the application password in PowerShell before starting Entri:

   ```powershell
   $env:ENTRI_DB_PASSWORD = "your-local-database-password"
   ```

   For persistence, use Windows **Environment Variables** to add a user-level
   `ENTRI_DB_PASSWORD`, then open a new terminal. Avoid putting the password in
   Java source, shell scripts, or `config.properties`.
4. Optionally set `ENTRI_DB_URL` and `ENTRI_DB_USER`. Defaults are
   `jdbc:mysql://localhost:3306/entri_db?serverTimezone=UTC` and `entri_app`.
   `config.example.properties` documents optional non-secret settings. Copy it
   to `config.properties` to override the default URL/user; environment variables
   take precedence. `config.properties` is excluded from Git.

## Currency display preference

After signing in, hover over **My Account** in the top-right menu and click
**Currency display** to choose PHP (₱) or USD ($). The choice is saved for that
user in MySQL and is loaded at the next sign-in. It changes only the displayed
symbol and formatting; it does not convert or change saved amounts or the
currency codes recorded on journal entries. The Journal Entry and Ledger
currency columns show the selected symbol in a compact width, while totals and
worksheet rows remain grouped by each record's saved currency code. New journal
entries start with the selected currency code; editing an existing entry keeps
its saved code.

## Compile and run on Windows

Open PowerShell in this folder and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run.ps1
```

If `ENTRI_DB_PASSWORD` is not already set, the script securely prompts for the
`entri_app` MySQL password without echoing it. The first compilation creates
`out`. Use `run.bat` from Command Prompt to start the same PowerShell launcher
and password prompt.

To view the interface without logging in or connecting to MySQL, launch the
sample-data, read-only preview:

```powershell
java --enable-native-access=javafx.graphics --module-path C:\javafx-sdk-21\lib --add-modules javafx.controls -cp "out;lib\mysql-connector-j.jar" main.java.com.entri.MainApp --preview
```

The login screen also includes an **Explore a sample preview** button. The
dashboard preview follows the desktop layout: Journal Entry and Ledger cards
side by side, a grouped 12-column worksheet below, then Recent Views with
View/Edit actions. Table totals are shown at the bottom; sample data is
illustrative only and is never saved. The profile menu and Developers page are
available in preview mode as well.

Equivalent manual commands (PowerShell):

```powershell
$FX = "C:\javafx-sdk-21\lib"
$CP = "lib\mysql-connector-j.jar"
javac --release 21 --module-path $FX --add-modules javafx.controls -cp $CP -d out (Get-ChildItem -Recurse src\main\java\*.java).FullName
Copy-Item -Recurse -Force src\main\resources\* out
java --module-path $FX --add-modules javafx.controls -cp "out;$CP" main.java.com.entri.MainApp
```

On macOS/Linux, set `FX_LIB` to the JavaFX SDK `lib` directory and place the
driver at `lib/mysql-connector-j.jar`:

```sh
mkdir -p out
javac --release 21 --module-path "$FX_LIB" --add-modules javafx.controls -cp lib/mysql-connector-j.jar -d out $(find src/main/java -name '*.java')
cp -R src/main/resources/. out/
java --module-path "$FX_LIB" --add-modules javafx.controls -cp "out:lib/mysql-connector-j.jar" main.java.com.entri.MainApp
```

Run the dependency-free password-hashing and accounting-validation tests on Windows with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\test.ps1
```

## Features and accounting behavior

- User registration/login uses salted PBKDF2-HMAC-SHA256 password hashes.
- Each new user receives a basic chart of accounts.
- Journal transactions support multiple debit and credit lines. A balanced
  transaction must have equal, positive debit and credit totals before posting.
- Dashboard cards show saved journal entries, actual ledger rows, a worksheet
  preview, and user-specific recent items; no sample financial transactions
  or balances are generated.
- Recent journal entries, ledger accounts, and worksheets are recorded in
  MySQL for the authenticated user. Updating an email address requires the
  current password.
- Ledger balances add debits and subtract credits for assets/expenses, and
  subtract debits and add credits for liabilities/equity/revenue. Running
  balances and worksheet rows are kept separate by currency; no exchange-rate
  conversion is implied.
- The worksheet derives its trial balance and adjustment columns from saved
  journal entries within the selected period. Adjusted trial balance is their
  sum; income statement and balance sheet columns classify adjusted balances
  by account type. Net income/loss is carried to equity and the balance sheet.
  The balance indicator checks each relevant section by currency. This is an
  unaudited working worksheet, not a statutory financial statement.
- Entries, lines, and ledger accounts are always queried within the logged-in
  user's ownership scope.

## Main classes

- `MainApp` owns login/registration, navigation, and the in-memory user session.
- `src/main/java/main/java/com/entri/MainApp.java` is the only application
  launcher. The test classes have their own `main` methods solely so `test.ps1`
  can run the checks without an additional test framework. Compiled `.class`
  files under `out` are generated by `run.ps1`; they are not duplicate sources.
- `Database` validates environment configuration and creates JDBC connections.
- `UserRepository` and `AccountingRepository` contain persistence operations.
- `AuthService` handles validation and password hashing.
- `AccountingService` validates and saves balanced journal entries.
- `JournalLine` models individual debit/credit lines; `JournalEntry` groups
  lines into a balanced, dated transaction.
- `AccountingSection` provides the shared JavaFX section contract;
  `JournalEntrySection`, `LedgerSection`, and `WorksheetSection` implement it.
- `EntriTheme` centralizes the desktop palette and reusable styles.
- `OverviewSection` and `RecentViewsSection` display current user-specific data.
- `DeveloperInfo` holds clearly editable placeholder team information.

## Troubleshooting

- **Cannot connect:** start MySQL, verify the schema, port, URL, user grants, and
  `ENTRI_DB_PASSWORD`. The application shows a safe connection error without
  exposing credentials.
- **JavaFX modules missing:** point `FX_LIB`/the PowerShell script at the `lib`
  folder of a JavaFX SDK compatible with your JDK.
- **JDBC driver missing:** place Connector/J at `lib\mysql-connector-j.jar` or
  update the classpath in the launch script.
- **Access denied:** verify the dedicated `entri_app` account and grant it
  access to `entri_db`; do not run the application as MySQL root.
