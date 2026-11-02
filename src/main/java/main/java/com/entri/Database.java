package main.java.com.entri;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Database {
    private static final String DEFAULT_URL = "";
    private static final String DEFAULT_USER = "";
    static final long STARTUP_TIMEOUT_MILLIS = 5_000;

    private Database() {}

    public static Connection getConnection() throws SQLException {
        ConnectionSettings settings = connectionSettings(loadLocalConfig(), System.getenv());
        try {
            return DriverManager.getConnection(withConnectTimeout(settings.url), settings.user, settings.password);
        } catch (SQLException ex) {
            throw new SQLException("Could not connect to Supabase PostgreSQL. Check the project connection "
                    + "URL, database username/password, network access, and that database\\entri_schema.sql "
                    + "has been run. SQLSTATE " + ex.getSQLState() + ".", ex);
        }
    }

    public static void initialize() throws SQLException {
        ConnectionSettings settings = connectionSettings(loadLocalConfig(), System.getenv());
        verifyDriver(settings.url);
        runStartupCheck(settings, Database::openPostgresConnection, STARTUP_TIMEOUT_MILLIS);
    }

    public static void testConnection() throws SQLException {
        try (Connection ignored = getConnection()) {
            // Opening a connection is the startup connectivity check.
        }
    }

    static ConnectionSettings connectionSettings(Properties config, Map<String, String> environment)
            throws SQLException {
        String url = environmentOrConfig(config, environment, "ENTRI_DB_URL", "entri.db.url", DEFAULT_URL);
        String user = environmentOrConfig(config, environment, "ENTRI_DB_USER", "entri.db.user", DEFAULT_USER);
        String password = environment.get("ENTRI_DB_PASSWORD");
        if (url.isBlank() || user.isBlank() || password == null || password.isBlank()) {
            throw new SQLException("Database configuration is incomplete. Set ENTRI_DB_URL, ENTRI_DB_USER, "
                    + "and ENTRI_DB_PASSWORD (or configure the non-secret URL and username in config.properties).");
        }
        return new ConnectionSettings(url, user, password);
    }

    static String withConnectTimeout(String url) {
        String replacement = "$1connectTimeout=5";
        if (url.matches("(?i).*([?&])connectTimeout=[^&]*.*")) {
            return url.replaceFirst("(?i)([?&])connectTimeout=[^&]*", replacement);
        }
        return url + (url.contains("?") ? "&" : "?") + "connectTimeout=5";
    }

    static void verifyDriver(String url) throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("PostgreSQL JDBC driver is unavailable. Check lib\\postgresql-42.7.14.jar.",
                    ex);
        }
        try {
            Driver driver = DriverManager.getDriver(url);
            if (!"org.postgresql.Driver".equals(driver.getClass().getName())) {
                throw new SQLException("The configured JDBC URL is not handled by the PostgreSQL driver.");
            }
        } catch (SQLException ex) {
            throw new SQLException("PostgreSQL JDBC driver is unavailable or the database URL is invalid.", ex);
        }
    }

    private static Connection openPostgresConnection(ConnectionSettings settings) throws SQLException {
        return DriverManager.getConnection(withConnectTimeout(settings.url), settings.user, settings.password);
    }

    static void runStartupCheck(ConnectionSettings settings, ConnectionProvider provider, long timeoutMillis)
            throws SQLException {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Startup timeout must be positive.");
        FutureTask<Void> attempt = new FutureTask<>(() -> {
            try (Connection connection = provider.connect(settings)) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new SQLException("Database startup check was cancelled.");
                }
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout((int) Math.max(1, (timeoutMillis + 999) / 1000));
                    try (ResultSet result = statement.executeQuery("SELECT 1")) {
                        if (!result.next() || result.getInt(1) != 1) {
                            throw new SQLException("The database startup query returned an unexpected result.");
                        }
                    }
                }
            }
            return null;
        });
        Thread worker = new Thread(attempt, "entri-database-startup");
        worker.setDaemon(true);
        worker.start();
        try {
            attempt.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            attempt.cancel(true);
            throw new SQLException("Database initialization did not complete within five seconds.", ex);
        } catch (InterruptedException ex) {
            attempt.cancel(true);
            Thread.currentThread().interrupt();
            throw new SQLException("Database initialization was interrupted.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SQLException) throw (SQLException) cause;
            throw new SQLException("Database initialization failed.", cause);
        }
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection connect(ConnectionSettings settings) throws SQLException;
    }

    static final class ConnectionSettings {
        final String url;
        final String user;
        final String password;

        ConnectionSettings(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }

    private static String environmentOrConfig(Properties config, Map<String, String> environment,
                                              String variable, String property, String fallback) {
        String value = environment.get(variable);
        if (value != null && !value.isBlank()) return value.trim();
        value = config.getProperty(property);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Properties loadLocalConfig() throws SQLException {
        Properties properties = new Properties();
        Path path = Path.of("config.properties");
        if (!Files.exists(path)) return properties;
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new SQLException("Could not read config.properties.", ex);
        }
    }
}
