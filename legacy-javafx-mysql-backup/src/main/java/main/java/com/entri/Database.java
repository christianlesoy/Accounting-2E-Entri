package main.java.com.entri;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Database {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/entri_db?serverTimezone=UTC";
    private Database() {}

    public static Connection getConnection() throws SQLException {
        String password = System.getenv("ENTRI_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new SQLException("Database password is not configured. Set ENTRI_DB_PASSWORD.");
        }
        Properties config = loadLocalConfig();
        String url = environmentOrConfig(config, "ENTRI_DB_URL", "entri.db.url", DEFAULT_URL);
        String user = environmentOrConfig(config, "ENTRI_DB_USER", "entri.db.user", "entri_app");
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException ex) {
            throw new SQLException("Could not connect to Entri's database. Check that MySQL is running, "
                    + "the entri_db schema exists, and the entri_app username/password and grants are correct. "
                    + "MySQL error " + ex.getErrorCode() + " (SQLSTATE " + ex.getSQLState() + ").", ex);
        }
    }

    public static void testConnection() throws SQLException {
        try (Connection ignored = getConnection()) {
            // Opening a connection is the startup connectivity check.
        }
    }

    private static String environmentOrConfig(Properties config, String environment, String property, String fallback) {
        String value = System.getenv(environment);
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
