package main.java.com.entri;

import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,32}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository users = new UserRepository();
    private final PasswordHasher hasher = new PasswordHasher();

    public User register(String username, String email, char[] password, char[] confirmation)
            throws SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(cleanUsername).matches()) {
            throw new IllegalArgumentException("Username must be 3–32 characters using letters, numbers, or underscore.");
        }
        if (cleanEmail.length() > 254 || !EMAIL.matcher(cleanEmail).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.length < 10) {
            throw new IllegalArgumentException("Password must contain at least 10 characters.");
        }
        if (!java.util.Arrays.equals(password, confirmation)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        return users.register(cleanUsername, cleanEmail, hasher.hash(password));
    }

    public User authenticate(String login, char[] password) throws SQLException {
        String identifier = login == null ? "" : login.trim();
        String hash = users.passwordHashFor(identifier);
        boolean valid = hash != null && password != null && hasher.verify(password, hash);
        if (!valid) throw new IllegalArgumentException("Invalid username/email or password.");
        return users.findByLogin(identifier);
    }

    public User updateEmail(User user, String email, char[] currentPassword) throws SQLException {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (cleanEmail.length() > 254 || !EMAIL.matcher(cleanEmail).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        String storedHash = users.passwordHashFor(user.getId());
        if (storedHash == null || currentPassword == null || !hasher.verify(currentPassword, storedHash)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        return users.updateEmail(user.getId(), cleanEmail);
    }
}
