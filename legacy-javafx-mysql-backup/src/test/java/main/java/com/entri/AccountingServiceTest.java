package main.java.com.entri;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

public final class AccountingServiceTest {
    public static void main(String[] args) throws Exception {
        Account cash = new Account(1, "1000", "Cash", "ASSET");
        Account revenue = new Account(2, "4000", "Service Revenue", "REVENUE");
        AccountingService service = new AccountingService();

        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Unequal lines", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("100.00"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("90.00")))),
                "Unbalanced entries must be rejected before persistence.");
        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Too precise", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("1.23456"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("1.23456")))),
                "Amounts with more than four decimal places must be rejected.");
        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Invalid currency", "US", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("1.00"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("1.00")))),
                "Invalid currency codes must be rejected.");

        JournalEntry entry = new JournalEntry(1, LocalDate.now(), "Balanced",
                "USD", "STANDARD", Arrays.asList(
                new JournalLine(cash, new BigDecimal("60.00"), BigDecimal.ZERO),
                new JournalLine(cash, new BigDecimal("40.00"), BigDecimal.ZERO),
                new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("100.00"))));
        require(entry.getDebitTotal().compareTo(new BigDecimal("100.00")) == 0,
                "Debit totals must include every line.");
        require(entry.getCreditTotal().compareTo(new BigDecimal("100.00")) == 0,
                "Credit totals must include every line.");
        System.out.println("AccountingServiceTest passed.");
    }

    private static void expectInvalid(ThrowingAction action, String message) throws Exception {
        try {
            action.run();
        } catch (IllegalArgumentException ex) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingAction { void run() throws Exception; }
}
