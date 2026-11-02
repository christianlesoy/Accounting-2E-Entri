package main.java.com.entri;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

public final class AccountingServiceTest {
    public static void main(String[] args) throws Exception {
        Account cash = new Account(1, "1000", "Cash", "ASSET");
        Account revenue = new Account(2, "4000", "Service Revenue", "REVENUE");
        AccountingService service = new AccountingService();

        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Unequal lines", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("100.00"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("90.00")))),
                "Unbalanced entries must be rejected before persistence.");
        AccountingService.validateAmount(new BigDecimal("1.23"));
        expectInvalid(() -> AccountingService.validateAmount(new BigDecimal("1.234")),
                "Amounts with more than two decimal places must be rejected.");
        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Too precise", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("1.234"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("1.234")))),
                "The service must reject amounts with more than two decimal places.");
        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Incomplete line", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, BigDecimal.ZERO, BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, BigDecimal.ZERO))),
                "Zero-value journal lines must be rejected.");
        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Same account", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("10.00"), BigDecimal.ZERO),
                        new JournalLine(cash, BigDecimal.ZERO, new BigDecimal("10.00")))),
                "A simple two-line transaction must use distinct accounts.");
        AccountingService.validateSimpleTransactionAccounts(Arrays.asList(
                new JournalLine(cash, new BigDecimal("6.00"), BigDecimal.ZERO),
                new JournalLine(cash, new BigDecimal("4.00"), BigDecimal.ZERO),
                new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("10.00"))));
        expectInvalid(() -> service.save(1, 0, LocalDate.now(), "Invalid currency", "US", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("1.00"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("1.00")))),
                "Invalid currency codes must be rejected.");
        expectInvalid(() -> service.entries(1, "", LocalDate.now(), LocalDate.now().minusDays(1)),
                "Date ranges with a start after the end must be rejected before database access.");
        expectInvalid(() -> service.entries(1, "", null, null, 0L),
                "Invalid account filter IDs must be rejected before database access.");
        expectInvalid(() -> service.addAccount(1, "bad code", "New Account", "ASSET"),
                "Invalid account codes must be rejected before database access.");
        expectInvalid(() -> service.addAccount(1, "1300", "", "ASSET"),
                "Empty account names must be rejected before database access.");
        expectInvalid(() -> service.addAccount(1, "1300", "   ", "ASSET"),
                "Whitespace-only account names must be rejected before database access.");
        expectInvalid(() -> service.addAccount(1, "1300", "New Account", "INVALID"),
                "Invalid account classifications must be rejected before database access.");
        expectInvalid(() -> service.updateAccount(1, 0, "1300", "New Account", "ASSET"),
                "Invalid account IDs must be rejected before database access.");
        expectInvalid(() -> service.updateAccount(1, 1, "bad code", "New Account", "ASSET"),
                "Invalid updated account codes must be rejected before database access.");
        expectInvalid(() -> service.updateAccount(1, 1, "1300", "", "ASSET"),
                "Empty updated account names must be rejected before database access.");
        expectInvalid(() -> service.updateAccount(1, 1, "1300", "New Account", "INVALID"),
                "Invalid updated account classifications must be rejected before database access.");
        SQLException duplicateCode = new SQLException(
                "duplicate key violates unique constraint \"uq_ledger_user_code\"", "23505");
        SQLException duplicateName = new SQLException(
                "duplicate key violates unique constraint \"uq_ledger_user_name\"", "23505");
        require("That account code is already in use.".equals(AccountingRepository.duplicateAccountMessage(
                        AccountingRepository.uniqueConstraintName(duplicateCode))),
                "Duplicate account codes must produce a specific message.");
        require("That account name is already in use.".equals(AccountingRepository.duplicateAccountMessage(
                        AccountingRepository.uniqueConstraintName(duplicateName))),
                "Duplicate account names must produce a specific message.");
        require("That account code or account name is already in use.".equals(
                        AccountingRepository.duplicateAccountMessage(null)),
                "Unknown unique constraints must retain a safe generic message.");

        JournalEntry entry = new JournalEntry(1, LocalDate.now(), "Balanced",
                "USD", "STANDARD", Arrays.asList(
                new JournalLine(cash, new BigDecimal("60.00"), BigDecimal.ZERO),
                new JournalLine(cash, new BigDecimal("40.00"), BigDecimal.ZERO),
                new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("100.00"))));
        require(entry.getDebitTotal().compareTo(new BigDecimal("100.00")) == 0,
                "Debit totals must include every line.");
        require(entry.getCreditTotal().compareTo(new BigDecimal("100.00")) == 0,
                "Credit totals must include every line.");
        require(entry.getLines().size() == 3,
                "Legitimate multi-line entries with account reuse must remain supported.");

        JournalEntry equalAmountsDifferentScales = new JournalEntry(2, LocalDate.now(), "Scaled amounts",
                "USD", "STANDARD", Arrays.asList(
                new JournalLine(cash, new BigDecimal("10.0"), BigDecimal.ZERO),
                new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("10.00"))));
        Map<String, JournalTotalsByCurrency.CurrencyTotals> equalTotals =
                JournalTotalsByCurrency.calculate(Arrays.asList(equalAmountsDifferentScales));
        require(equalTotals.get("USD").isBalanced(),
                "Equal debit and credit totals with different scales must be balanced.");

        JournalEntry usdDebit = new JournalEntry(3, LocalDate.now(), "USD debit", "USD", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("10.00"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, BigDecimal.ZERO)));
        JournalEntry phpCredit = new JournalEntry(4, LocalDate.now(), "PHP credit", "PHP", "STANDARD",
                Arrays.asList(new JournalLine(cash, BigDecimal.ZERO, BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("10.00"))));
        Map<String, JournalTotalsByCurrency.CurrencyTotals> separateCurrencyTotals =
                JournalTotalsByCurrency.calculate(Arrays.asList(usdDebit, phpCredit));
        require(separateCurrencyTotals.size() == 2,
                "Journal totals must remain separated by saved currency.");
        require(!separateCurrencyTotals.get("USD").isBalanced()
                        && !separateCurrencyTotals.get("PHP").isBalanced(),
                "Opposing differences in separate currencies must not be combined into a balanced total.");
        require(!JournalTotalsByCurrency.calculate(Arrays.asList(
                        new JournalEntry(5, LocalDate.now(), "Unbalanced", "USD", "STANDARD",
                                Arrays.asList(new JournalLine(cash, new BigDecimal("12.00"), BigDecimal.ZERO),
                                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("11.00"))))))
                        .get("USD").isBalanced(),
                "Unequal debit and credit totals must be reported as unbalanced.");
        require(JournalTotalsByCurrency.calculate(Arrays.asList()).isEmpty(),
                "No matching entries must not create a balanced status.");

        JournalEntry savedPhpEntry = new JournalEntry(6, LocalDate.now(), "Saved currency", "PHP", "STANDARD",
                Arrays.asList(new JournalLine(cash, new BigDecimal("1250"), BigDecimal.ZERO),
                        new JournalLine(revenue, BigDecimal.ZERO, new BigDecimal("1250"))));
        require("₱1,250.00".equals(CurrencyFormatter.format(
                        savedPhpEntry.getDebitTotal(), savedPhpEntry.getCurrency())),
                "Journal amounts must format using their saved currency code.");
        require("PHP".equals(savedPhpEntry.getCurrency()),
                "Journal entries must retain their saved currency code for presentation.");
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
