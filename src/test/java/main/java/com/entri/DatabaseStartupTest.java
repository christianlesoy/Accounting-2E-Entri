package main.java.com.entri;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatabaseStartupTest {
    public static void main(String[] args) throws Exception {
        testConfigurationValidationAndPrecedence();
        testConnectTimeoutParameter();
        testDriverAvailability();
        testStartupQuery();
        testConnectionFailure();
        testStartupTimeout();
        System.out.println("DatabaseStartupTest passed.");
    }

    private static void testConfigurationValidationAndPrecedence() throws Exception {
        Properties config = new Properties();
        config.setProperty("entri.db.url", "jdbc:postgresql://config-host/db");
        config.setProperty("entri.db.user", "config-user");
        Map<String, String> environment = new HashMap<>();
        environment.put("ENTRI_DB_PASSWORD", " secret ");
        Database.ConnectionSettings settings = Database.connectionSettings(config, environment);
        require(settings.url.equals("jdbc:postgresql://config-host/db"), "Config URL should be loaded.");
        require(settings.user.equals("config-user"), "Config user should be loaded.");
        require(settings.password.equals(" secret "), "Database passwords must not be trimmed.");

        environment.put("ENTRI_DB_URL", "jdbc:postgresql://environment-host/db");
        environment.put("ENTRI_DB_USER", "environment-user");
        settings = Database.connectionSettings(config, environment);
        require(settings.url.contains("environment-host") && settings.user.equals("environment-user"),
                "Environment settings should override config properties.");

        environment.remove("ENTRI_DB_PASSWORD");
        expectSqlFailure(() -> Database.connectionSettings(config, environment),
                "Missing database password must fail configuration validation.");
        environment.put("ENTRI_DB_PASSWORD", "password");
        config.clear();
        environment.remove("ENTRI_DB_URL");
        environment.remove("ENTRI_DB_USER");
        expectSqlFailure(() -> Database.connectionSettings(config, environment),
                "Missing URL and username must fail configuration validation.");
    }

    private static void testConnectTimeoutParameter() {
        require(Database.STARTUP_TIMEOUT_MILLIS == 5_000,
                "Startup must use the five-second timeout.");
        require(Database.withConnectTimeout("jdbc:postgresql://host/db")
                        .equals("jdbc:postgresql://host/db?connectTimeout=5"),
                "Connection timeout should be added when missing.");
        require(Database.withConnectTimeout("jdbc:postgresql://host/db?sslmode=require")
                        .equals("jdbc:postgresql://host/db?sslmode=require&connectTimeout=5"),
                "Connection timeout should preserve existing URL parameters.");
        require(Database.withConnectTimeout("jdbc:postgresql://host/db?connectTimeout=20&sslmode=require")
                        .equals("jdbc:postgresql://host/db?connectTimeout=5&sslmode=require"),
                "Existing connection timeout should be replaced.");
    }

    private static void testDriverAvailability() throws Exception {
        Database.verifyDriver("jdbc:postgresql://localhost/test");
    }

    private static void testStartupQuery() throws Exception {
        AtomicBoolean connectionClosed = new AtomicBoolean();
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean resultClosed = new AtomicBoolean();
        AtomicInteger timeoutSeconds = new AtomicInteger();
        Database.ConnectionSettings settings = settings();
        Database.runStartupCheck(settings, ignored -> mockConnection(
                connectionClosed, statementClosed, resultClosed, timeoutSeconds, 1, null), 1_000);
        require(connectionClosed.get() && statementClosed.get() && resultClosed.get(),
                "Startup check should close all JDBC resources.");
        require(timeoutSeconds.get() == 1, "The startup query should have a JDBC query timeout.");

        expectSqlFailure(() -> Database.runStartupCheck(settings, ignored -> mockConnection(
                new AtomicBoolean(), new AtomicBoolean(), new AtomicBoolean(),
                new AtomicInteger(), 0, null), 1_000),
                "An unexpected startup query result must fail initialization.");
        expectSqlFailure(() -> Database.runStartupCheck(settings, ignored -> mockConnection(
                new AtomicBoolean(), new AtomicBoolean(), new AtomicBoolean(),
                new AtomicInteger(), 1, new SQLException("simulated query failure")), 1_000),
                "A failed startup query must fail initialization.");
    }

    private static void testConnectionFailure() throws Exception {
        expectSqlFailure(() -> Database.runStartupCheck(settings(), ignored -> {
            throw new SQLException("simulated connection failure");
        }, 1_000), "A failed JDBC connection must fail initialization.");
        Database.ConnectionSettings localClosedPort =
                new Database.ConnectionSettings("jdbc:postgresql://127.0.0.1:1/test", "test", "test");
        expectSqlFailure(() -> Database.runStartupCheck(localClosedPort, settings ->
                java.sql.DriverManager.getConnection(Database.withConnectTimeout(settings.url),
                        settings.user, settings.password), 1_500),
                "A real PostgreSQL JDBC connection refusal must fail initialization.");
    }

    private static void testStartupTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        long start = System.nanoTime();
        try {
            Database.runStartupCheck(settings(), ignored -> {
                boolean released = false;
                while (!released) {
                    try {
                        released = release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignoredInterrupt) {
                        // Simulate a JDBC connect call that does not respond to cancellation.
                    }
                }
                throw new SQLException("simulated delayed connection");
            }, 150);
            throw new AssertionError("A startup attempt exceeding its deadline must fail.");
        } catch (SQLException expected) {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            require(elapsedMillis < 1_500, "Startup timeout should return promptly; took "
                    + elapsedMillis + " ms.");
        } finally {
            release.countDown();
        }
    }

    private static Connection mockConnection(AtomicBoolean connectionClosed,
                                             AtomicBoolean statementClosed,
                                             AtomicBoolean resultClosed,
                                             AtomicInteger timeoutSeconds,
                                             int resultValue,
                                             SQLException queryFailure) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                DatabaseStartupTest.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next": return true;
                        case "getInt": return resultValue;
                        case "close": resultClosed.set(true); return null;
                        case "isClosed": return resultClosed.get();
                        default: return defaultValue(method.getReturnType());
                    }
                });
        Statement statement = (Statement) Proxy.newProxyInstance(
                DatabaseStartupTest.class.getClassLoader(), new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setQueryTimeout": timeoutSeconds.set((Integer) args[0]); return null;
                        case "executeQuery":
                            require("SELECT 1".equals(args[0]), "Startup must execute SELECT 1.");
                            if (queryFailure != null) throw queryFailure;
                            return resultSet;
                        case "close": statementClosed.set(true); return null;
                        case "isClosed": return statementClosed.get();
                        default: return defaultValue(method.getReturnType());
                    }
                });
        return (Connection) Proxy.newProxyInstance(
                DatabaseStartupTest.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "createStatement": return statement;
                        case "close": connectionClosed.set(true); return null;
                        case "isClosed": return connectionClosed.get();
                        default: return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private static Database.ConnectionSettings settings() {
        return new Database.ConnectionSettings("jdbc:postgresql://localhost/test", "test", "test");
    }

    private static void expectSqlFailure(SqlOperation operation, String message) throws Exception {
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (SQLException expected) {
            // Expected failure.
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws Exception;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
