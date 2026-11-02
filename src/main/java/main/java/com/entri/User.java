package main.java.com.entri;

import java.time.LocalDateTime;

public final class User {
    private final long id;
    private final String username;
    private final String email;
    private final LocalDateTime createdAt;

    public User(long id, String username, String email) {
        this(id, username, email, null);
    }

    public User(long id, String username, String email, LocalDateTime createdAt) {
        if (id <= 0 || username == null || email == null) {
            throw new IllegalArgumentException("Invalid user.");
        }
        this.id = id;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
