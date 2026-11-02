package main.java.com.entri;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JournalEntry {
    private final long id;
    private final LocalDate date;
    private final String particulars;
    private final String currency;
    private final String type;
    private final List<JournalLine> lines;

    public JournalEntry(long id, LocalDate date, String particulars, String currency, String type,
                        Account debitAccount, Account creditAccount, BigDecimal amount) {
        this(id, date, particulars, currency, type,
                createLines(debitAccount, creditAccount, amount));
    }

    public JournalEntry(long id, LocalDate date, String particulars, String currency, String type,
                        List<JournalLine> lines) {
        this.id = id;
        this.date = date;
        this.particulars = particulars;
        this.currency = currency;
        this.type = type;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    private static List<JournalLine> createLines(Account debitAccount, Account creditAccount,
                                                  BigDecimal amount) {
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine(debitAccount, amount, BigDecimal.ZERO));
        lines.add(new JournalLine(creditAccount, BigDecimal.ZERO, amount));
        return lines;
    }

    public long getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getParticulars() { return particulars; }
    public String getCurrency() { return currency; }
    public String getType() { return type; }
    public List<JournalLine> getLines() { return lines; }
    public BigDecimal getDebitTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine line : lines) {
            total = total.add(line.getDebit());
        }
        return total;
    }
    public BigDecimal getCreditTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (JournalLine line : lines) {
            total = total.add(line.getCredit());
        }
        return total;
    }
    public BigDecimal getAmount() { return getDebitTotal(); }
    public Account getDebitAccount() {
        for (JournalLine line : lines) {
            if (line.getDebit().signum() > 0) {
                return line.getAccount();
            }
        }
        return null;
    }
    public Account getCreditAccount() {
        for (JournalLine line : lines) {
            if (line.getCredit().signum() > 0) {
                return line.getAccount();
            }
        }
        return null;
    }
}
