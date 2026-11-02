package main.java.com.entri;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AccountingService {
    private final AccountingRepository repository = new AccountingRepository();

    public List<Account> accounts(long userId) throws SQLException { return repository.accounts(userId); }
    public List<JournalEntry> entries(long userId, String filter) throws SQLException {
        return repository.entries(userId, filter);
    }
    public List<JournalEntry> entries(long userId, String filter, LocalDate from, LocalDate to)
            throws SQLException {
        return entries(userId, filter, from, to, null);
    }
    public List<JournalEntry> entries(long userId, String filter, LocalDate from, LocalDate to,
                                      Long accountId) throws SQLException {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The date range start must not be after its end.");
        }
        if (accountId != null && accountId <= 0) {
            throw new IllegalArgumentException("Choose a valid ledger account filter.");
        }
        return repository.entries(userId, filter, from, to, accountId);
    }
    public Account addAccount(long userId, String code, String name, String type) throws SQLException {
        AccountDraft draft = validateAccountDetails(code, name, type);
        String cleanCode = draft.code;
        String cleanName = draft.name;
        String cleanType = draft.type;
        return repository.addAccount(userId, cleanCode, cleanName, cleanType);
    }
    public Account updateAccount(long userId, long accountId, String code, String name, String type)
            throws SQLException {
        if (accountId <= 0) throw new IllegalArgumentException("Select a valid ledger account.");
        AccountDraft draft = validateAccountDetails(code, name, type);
        return repository.updateAccount(userId, accountId, draft.code, draft.name, draft.type);
    }

    private static AccountDraft validateAccountDetails(String code, String name, String type) {
        String cleanCode = code == null ? "" : code.trim();
        String cleanName = name == null ? "" : name.trim();
        String cleanType = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!cleanCode.matches("[A-Za-z0-9-]{1,20}")) {
            throw new IllegalArgumentException("Account code must use 1 to 20 letters, numbers, or hyphens.");
        }
        if (cleanName.isEmpty() || cleanName.length() > 100) {
            throw new IllegalArgumentException("Enter an account name (up to 100 characters).");
        }
        if (!java.util.Arrays.asList("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE")
                .contains(cleanType)) {
            throw new IllegalArgumentException("Choose a valid account classification.");
        }
        return new AccountDraft(cleanCode, cleanName, cleanType);
    }

    private static final class AccountDraft {
        final String code;
        final String name;
        final String type;
        AccountDraft(String code, String name, String type) {
            this.code = code;
            this.name = name;
            this.type = type;
        }
    }
    public void removeAccount(long userId, long accountId) throws SQLException {
        repository.removeAccount(userId, accountId);
    }
    public JournalEntry entry(long userId, long id) throws SQLException { return repository.getEntry(userId, id); }
    public List<AccountingRepository.LedgerLine> ledger(long userId, long accountId) throws SQLException {
        return repository.ledger(userId, accountId);
    }
    public LedgerCalculator.Result ledgerPeriod(long userId, long accountId, LocalDate from, LocalDate to)
            throws SQLException {
        if (accountId <= 0) throw new IllegalArgumentException("Select a valid ledger account.");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The ledger start date must not be after its end date.");
        }
        return LedgerCalculator.calculate(repository.ledgerPostings(userId, accountId, to), from, to);
    }
    public List<AccountingRepository.WorksheetRow> worksheet(long userId) throws SQLException {
        return repository.worksheet(userId, null, null);
    }
    public List<AccountingRepository.WorksheetRow> worksheet(long userId, LocalDate from, LocalDate to) throws SQLException {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The reporting period start must not be after its end.");
        }
        return repository.worksheet(userId, from, to);
    }
    public List<RecentView> recentViews(long userId) throws SQLException { return repository.recentViews(userId); }
    public void recordRecent(long userId, String type, long itemId, String name) throws SQLException {
        repository.recordRecent(userId, type, itemId, name);
    }
    public long save(long userId, long id, LocalDate date, String particulars, String currency, String type,
                     Account debit, Account credit, BigDecimal amount) throws SQLException {
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine(debit, amount, BigDecimal.ZERO));
        lines.add(new JournalLine(credit, BigDecimal.ZERO, amount));
        return save(userId, id, date, particulars, currency, type, lines);
    }

    public long save(long userId, long id, LocalDate date, String particulars, String currency, String type,
                     List<JournalLine> lines) throws SQLException {
        String description = particulars == null ? "" : particulars.trim();
        String curr = currency == null ? "" : currency.trim().toUpperCase(java.util.Locale.ROOT);
        if (date == null) throw new IllegalArgumentException("Choose an entry date.");
        if (description.isEmpty() || description.length() > 500) throw new IllegalArgumentException("Particulars are required (maximum 500 characters).");
        if (!curr.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter code, such as USD.");
        if (lines == null || lines.size() < 2) throw new IllegalArgumentException("Add at least two journal lines.");
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (JournalLine line : lines) {
            if (line == null || line.getAccount() == null) throw new IllegalArgumentException("Select an account for every line.");
            BigDecimal debit = line.getDebit();
            BigDecimal credit = line.getCredit();
            validateAmount(debit);
            validateAmount(credit);
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new IllegalArgumentException("Every journal line must have a positive debit or credit.");
            }
            debitTotal = debitTotal.add(debit);
            creditTotal = creditTotal.add(credit);
        }
        validateSimpleTransactionAccounts(lines);
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("Debits must equal credits before this entry can be posted.");
        }
        if (!"STANDARD".equals(type) && !"ADJUSTMENT".equals(type)) {
            throw new IllegalArgumentException("Choose a valid entry type.");
        }
        return repository.saveEntry(userId, new JournalEntry(id, date, description, curr, type, lines));
    }

    static void validateSimpleTransactionAccounts(List<JournalLine> lines) {
        if (hasSameAccountForSimpleTransaction(lines)) {
            throw new IllegalArgumentException(
                    "Choose different accounts for the debit and credit sides of a two-line transaction.");
        }
    }

    static boolean hasSameAccountForSimpleTransaction(List<JournalLine> lines) {
        if (lines == null || lines.size() != 2) return false;
        JournalLine first = lines.get(0);
        JournalLine second = lines.get(1);
        boolean oppositeSides = first.getDebit().signum() > 0 && second.getCredit().signum() > 0
                || first.getCredit().signum() > 0 && second.getDebit().signum() > 0;
        return oppositeSides && first.getAccount().getId() == second.getAccount().getId();
    }

    public void delete(long userId, long id) throws SQLException { repository.deleteEntry(userId, id); }

    static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2 || amount.precision() > 19
                || amount.compareTo(new BigDecimal("1000000000000000")) >= 0) {
            throw new IllegalArgumentException("Amounts must be non-negative with no more than two decimal places.");
        }
    }
}
