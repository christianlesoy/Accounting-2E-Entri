package main.java.com.entri;

import java.sql.SQLException;

public final class AuthServiceValidationTest {
    public static void main(String[] args) throws Exception {
        AuthService auth = new AuthService();
        expectInvalid(() -> auth.register("ab", "person@example.com",
                "long-password".toCharArray(), "long-password".toCharArray()));
        expectInvalid(() -> auth.register("valid_user", "not-an-email",
                "long-password".toCharArray(), "long-password".toCharArray()));
        expectInvalid(() -> auth.register("valid_user", "person@example.com",
                "short".toCharArray(), "short".toCharArray()));
        expectInvalid(() -> auth.register("valid_user", "person@example.com",
                "long-password".toCharArray(), "different-password".toCharArray()));
        System.out.println("AuthServiceValidationTest passed.");
    }

    private static void expectInvalid(SqlOperation operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Invalid registration data should be rejected before database access.");
        } catch (IllegalArgumentException expected) {
            // Expected validation result.
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
