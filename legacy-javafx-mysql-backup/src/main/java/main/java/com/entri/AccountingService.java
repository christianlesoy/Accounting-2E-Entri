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
    public JournalEntry entry(long userId, long id) throws SQLException { return repository.getEntry(userId, id); }
    public List<AccountingRepository.LedgerLine> ledger(long userId, long accountId) throws SQLException {
        return repository.ledger(userId, accountId);
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
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("Debits must equal credits before this entry can be posted.");
        }
        if (!"STANDARD".equals(type) && !"ADJUSTMENT".equals(type)) {
            throw new IllegalArgumentException("Choose a valid entry type.");
        }
        return repository.saveEntry(userId, new JournalEntry(id, date, description, curr, type, lines));
    }
    public void delete(long userId, long id) throws SQLException { repository.deleteEntry(userId, id); }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 4 || amount.precision() > 19
                || amount.compareTo(new BigDecimal("1000000000000000")) >= 0) {
            throw new IllegalArgumentException("Amounts must be non-negative with no more than four decimal places.");
        }
    }
}
