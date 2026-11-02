package main.java.com.entri;

public final class PasswordHasherTest {
    public static void main(String[] args) {
        PasswordHasher hasher = new PasswordHasher();
        char[] password = "correct horse battery".toCharArray();
        String first = hasher.hash(password);
        String second = hasher.hash(password);
        require(!first.equals(second), "Hashes must use unique random salts.");
        require(hasher.verify(password, first), "The original password should verify.");
        require(hasher.verify(password, second), "The original password should verify against each hash.");
        require(!hasher.verify("incorrect password".toCharArray(), first), "A different password must not verify.");
        require(!hasher.verify(password, "invalid"), "Malformed hashes must fail safely.");
        java.util.Arrays.fill(password, '\0');
        System.out.println("PasswordHasherTest passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
