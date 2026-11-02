package main.java.com.entri;

import java.sql.*;

final class UserRepository {
    User register(String username, String email, String passwordHash) throws SQLException {
        String insertUser = "INSERT INTO users(username,email,password_hash) VALUES(?,?,?)";
        String[] names = {"Cash", "Accounts Receivable", "Supplies", "Equipment",
                "Accounts Payable", "Owner's Capital", "Service Revenue", "Rent Expense",
                "Utilities Expense"};
        String[] codes = {"1000", "1100", "1200", "1500", "2000", "3000", "4000", "5000", "5100"};
        String[] types = {"ASSET", "ASSET", "ASSET", "ASSET", "LIABILITY", "EQUITY",
                "REVENUE", "EXPENSE", "EXPENSE"};
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement p = c.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1, username);
                    p.setString(2, email);
                    p.setString(3, passwordHash);
                    p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("User creation did not return an ID.");
                        id = keys.getLong(1);
                    }
                }
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO ledger_accounts(user_id,account_code,account_name,account_type) VALUES(?,?,?,?)")) {
                    for (int i = 0; i < names.length; i++) {
                        p.setLong(1, id);
                        p.setString(2, codes[i]);
                        p.setString(3, names[i]);
                        p.setString(4, types[i]);
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                User created = findById(c, id);
                c.commit();
                return created;
            } catch (SQLException ex) {
                c.rollback();
                if ("23000".equals(ex.getSQLState())) {
                    throw new IllegalArgumentException("That username or email is already registered.", ex);
                }
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    String passwordHashFor(String login) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT password_hash FROM users WHERE username=? OR email=? LIMIT 1")) {
            p.setString(1, login);
            p.setString(2, login.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    String passwordHashFor(long userId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT password_hash FROM users WHERE id=?")) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    User updateEmail(long userId, String email) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE users SET email=? WHERE id=?")) {
            p.setString(1, email);
            p.setLong(2, userId);
            try {
                if (p.executeUpdate() != 1) throw new SQLException("Account could not be updated.");
            } catch (SQLException ex) {
                if ("23000".equals(ex.getSQLState())) {
                    throw new IllegalArgumentException("That email address is already in use.", ex);
                }
                throw ex;
            }
        }
        return findById(userId);
    }

    User findByLogin(String login) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,username,email,created_at FROM users WHERE username=? OR email=? LIMIT 1")) {
            p.setString(1, login);
            p.setString(2, login.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Authenticated user could not be loaded.");
                return new User(r.getLong("id"), r.getString("username"), r.getString("email"),
                        r.getTimestamp("created_at").toLocalDateTime());
            }
        }
    }

    private User findById(long id) throws SQLException {
        try (Connection c = Database.getConnection()) { return findById(c, id); }
    }

    private User findById(Connection c, long id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id,username,email,created_at FROM users WHERE id=?")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Newly created user could not be loaded.");
                return new User(r.getLong("id"), r.getString("username"), r.getString("email"),
                        r.getTimestamp("created_at").toLocalDateTime());
            }
        }
    }
}
