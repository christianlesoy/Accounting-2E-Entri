package main.java.com.entri;

import java.math.BigDecimal;

public final class JournalLine {
    private final Account account;
    private final BigDecimal debit;
    private final BigDecimal credit;

    public JournalLine(Account account, BigDecimal debit, BigDecimal credit) {
        if (account == null || debit == null || credit == null
                || debit.signum() < 0 || credit.signum() < 0
                || debit.signum() > 0 && credit.signum() > 0) {
            throw new IllegalArgumentException("Invalid journal line.");
        }
        this.account = account;
        this.debit = debit;
        this.credit = credit;
    }

    public Account getAccount() { return account; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
}
