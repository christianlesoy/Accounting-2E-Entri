# Entri

Entri is a native JavaFX accounting desktop application backed by Supabase
PostgreSQL. It uses plain `javac`/`java`; no Maven or Gradle build is required.

## Requirements

- JDK 21
- JavaFX SDK for JDK 21
- A Supabase project
- PostgreSQL JDBC driver

Install JDK 21 from an OpenJDK distribution, download the JavaFX SDK for JDK 21
from [Gluon](https://gluonhq.com/products/javafx/), and download the latest
PostgreSQL JDBC driver from
[jdbc.postgresql.org](https://jdbc.postgresql.org/download/).
Extract the JavaFX SDK to `C:\javafx-sdk-21` and copy the driver jar to
`lib\postgresql-42.7.14.jar` (create `lib` if needed). These jars remain local
and are excluded from Git.

The launch scripts expect JavaFX at `C:\javafx-sdk-21\lib` and the PostgreSQL
driver at `lib\postgresql-42.7.14.jar`. Edit the script variables if you
installed them elsewhere. The launcher selects an installed JDK 21 so the
JavaFX runtime matches its supported Java version. If JDK 21 is installed in a
custom location, set `ENTRI_JDK_HOME` to its installation folder before
launching. Keep dependency jars out of source control.

## VS Code Java setup

The project includes `.vscode/settings.json` so the Java extension uses
`src/main/java` as its source root and can find JavaFX and the PostgreSQL driver.
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

## Supabase database setup

1. Create a project at [supabase.com](https://supabase.com/dashboard). Save the
   database password you choose; it is different from your Supabase account
   password.
2. In your project, open **SQL Editor**, paste the contents of
   `database/entri_schema.sql`, and run it. This creates Entri's PostgreSQL
   tables. Running the script again is safe and does not erase rows.
3. Open **Connect** in the project dashboard and choose **Session pooler**.
   Copy the host, port, database name, and username shown there. Session mode
   supports this app's JDBC prepared statements and works on IPv4 networks.
   Supabase's [PostgreSQL connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres)
   describes the connection options and where to find them.
4. Set the connection details in the PowerShell terminal before running Entri.
   Replace the example values with the values from **Connect**:

   ```powershell
   $env:ENTRI_DB_URL = "jdbc:postgresql://YOUR-POOLER-HOST:5432/postgres?sslmode=require"
   $env:ENTRI_DB_USER = "postgres.YOUR-PROJECT-REF"
   ```

   You can also copy `config.example.properties` to `config.properties` and
   put the non-secret URL and username there. Environment variables take
   precedence, and `config.properties` is excluded from Git.
5. Run the launcher. If `ENTRI_DB_PASSWORD` is not set, it securely prompts for
   the Supabase database password without echoing it. Do not place that password
   in Java source, `config.properties`, or committed files.

The app connects to hosted PostgreSQL over TLS through the session pooler using
the database username and password. For this first-project setup, it keeps Entri's
existing app-level login and ownership checks. Those are not Supabase Auth or
database-enforced row-level security; do not use this design for sensitive or
production data. Supabase explains the distinctions in its
[data security guide](https://supabase.com/docs/guides/database/secure-data).
The schema creates a fresh online database; it does not migrate any data from
your previous local MySQL database.

## Currency display preference

After signing in, hover over **My Account** in the top-right menu and click
**Currency display** to choose PHP (₱) or USD ($). The choice is saved for that
user in Supabase and is loaded at the next sign-in. It changes only the displayed
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
Supabase database password without echoing it. The first compilation creates
`out`. Use `run.bat` from Command Prompt to start the same PowerShell launcher
and password prompt.

To view the interface without logging in or connecting to Supabase, launch the
sample-data, read-only preview:

```powershell
& "$env:ENTRI_JDK_HOME\bin\java.exe" --enable-native-access=javafx.graphics --module-path C:\javafx-sdk-21\lib --add-modules javafx.controls -cp "out;lib\postgresql-42.7.14.jar" main.java.com.entri.MainApp --preview
```

Set `ENTRI_JDK_HOME` to the JDK 21 installation folder first if it is not
already set. The launcher script detects standard JDK 21 installations
automatically.

The login screen also includes an **Explore a sample preview** button. The
dashboard preview follows the desktop layout: Journal Entry and Ledger cards
side by side, a grouped 12-column worksheet below, then Recent Views with
View/Edit actions. Table totals are shown at the bottom; sample data is
illustrative only and is never saved. The profile menu and Developers page are
available in preview mode as well.

Equivalent manual commands (PowerShell):

```powershell
$FX = "C:\javafx-sdk-21\lib"
$CP = "lib\postgresql-42.7.14.jar"
javac --release 21 --module-path $FX --add-modules javafx.controls -cp $CP -d out (Get-ChildItem -Recurse src\main\java\*.java).FullName
Copy-Item -Recurse -Force src\main\resources\* out
java --module-path $FX --add-modules javafx.controls -cp "out;$CP" main.java.com.entri.MainApp
```

On macOS/Linux, set `FX_LIB` to the JavaFX SDK `lib` directory and place the
driver at `lib/postgresql-42.7.14.jar`:

```sh
mkdir -p out
javac --release 21 --module-path "$FX_LIB" --add-modules javafx.controls -cp lib/postgresql-42.7.14.jar -d out $(find src/main/java -name '*.java')
cp -R src/main/resources/. out/
java --module-path "$FX_LIB" --add-modules javafx.controls -cp "out:lib/postgresql-42.7.14.jar" main.java.com.entri.MainApp
```

Run the dependency-free password, accounting-validation, currency-formatting,
and worksheet-calculation tests on Windows with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\test.ps1
```

## Features and accounting behavior

- The JavaFX window can resize from a practical 720-by-520 minimum; toolbars
  and filters wrap, the sidebar scales with the window and can be collapsed,
  and dashboard cards change columns at narrower widths. On wide dashboards,
  Journal Entry and Ledger share the first row; the grouped 12-column Worksheet
  and Recent Views each occupy a full-width row below. The header keeps
  branding, Home/new-entry actions, and profile controls in aligned sections,
  switching to compact labels when needed. Accounting tables fill available
  space, while the 12-column worksheet keeps its own horizontal scrollbar on
  narrow windows.
- User registration/login uses salted PBKDF2-HMAC-SHA256 password hashes.
- Each new user receives a basic chart of accounts.
- Journal transactions support multiple debit and credit lines. A balanced
  transaction must have equal, positive debit and credit totals before posting.
- Dashboard cards show saved journal entries, actual ledger rows, a worksheet
  preview, and user-specific recent items; no sample financial transactions
  or balances are generated.
- Recent journal entries, ledger accounts, and worksheets are recorded in
  Supabase PostgreSQL for the authenticated user. Updating an email address
  requires the current password.
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
- After successful sign-in, the Accounts screen opens first. The sidebar also
  provides Home, New Transaction, Transactions, Accounts, General Journal,
  General Ledger, Balance Sheet, and the full 12-Column Worksheet. Transactions
  and General Journal reuse the existing journal-entry screen; General Ledger
  reuses Ledger. Balance Sheet uses a separate cumulative calculation of
  saved journal postings through the selected report date, grouped and balanced
  by saved currency; it does not convert currencies. It shows nonzero account
  balances, earnings, category totals, and the difference between assets and
  liabilities, equity, and earnings. Because account types do not identify
  retained earnings or fiscal-year boundaries, it does not perform a closing
  entry or independently calculate retained earnings. This is an unaudited
  report, not a statutory financial statement.
- Entries, lines, and ledger accounts are always queried within the logged-in
  user's ownership scope.
- Journal Entry provides Add, View, Edit, and selection-aware Delete actions.
  Search and date filters can be cleared separately from Refresh, which keeps
  the active filters.
- Journal Entry and the 12-Column Worksheet can export the currently displayed
  rows to UTF-8 CSV files that open in spreadsheet apps. Journal exports keep
  each entry's saved currency code and exact debit/credit totals; worksheet
  exports include the selected report period and clearly named debit/credit
  columns for each worksheet section. The Balance Sheet Options menu provides
  Refresh, Change Report Date, and Export CSV; CSV export reloads the report
  for its selected date before writing account rows and per-currency totals.
  CSV text is quoted and formula-like text is escaped. These are CSV files,
  not native Excel `.xlsx` workbooks.
- Accounts, Ledger, and the 12-column Worksheet provide account Add, Edit, and Delete
  actions. Editing keeps the account ID so existing journal links remain valid;
  changing classification is blocked when posted activity exists, and deleting
  an account with journal lines is refused rather than cascading financial data.
- `Ctrl+N` opens a new journal entry. `Ctrl+F` focuses search on Journal Entry,
  Ledger, or Recent Views; from the dashboard it opens Journal Entry search.

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

- **Cannot connect:** verify the Supabase project is running, the SQL schema was
  created, the session-pooler host/port/user are correct, and
  `ENTRI_DB_PASSWORD`. The application shows a safe connection error without
  exposing credentials.
- **JavaFX modules missing:** point `FX_LIB`/the PowerShell script at the `lib`
  folder of a JavaFX SDK compatible with your JDK.
- **JDBC driver missing:** place the PostgreSQL driver at
  `lib\postgresql-42.7.14.jar` or
  update the classpath in the launch script.
- **Access denied:** verify the database username and password from the
  Supabase project's **Connect** panel.
