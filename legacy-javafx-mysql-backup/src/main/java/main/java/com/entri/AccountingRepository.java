package main.java.com.entri;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

final class AccountingRepository {
    List<Account> accounts(long userId) throws SQLException {
        List<Account> result = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT id,account_code,account_name,account_type FROM ledger_accounts WHERE user_id=? ORDER BY account_code")) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new Account(r.getLong(1), r.getString(2), r.getString(3), r.getString(4)));
            }
        }
        return result;
    }

    JournalEntry getEntry(long userId, long entryId) throws SQLException {
        String sql = "SELECT id,entry_date,particulars,currency,entry_type FROM journal_entries WHERE user_id=? AND id=?";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId);
            p.setLong(2, entryId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                long id = r.getLong(1);
                java.time.LocalDate date = r.getDate(2).toLocalDate();
                String particulars = r.getString(3);
                String currency = r.getString(4);
                String entryType = r.getString(5);
                List<JournalLine> lines = new ArrayList<>();
                String lineSql = "SELECT a.id,a.account_code,a.account_name,a.account_type,l.debit,l.credit "
                        + "FROM journal_entry_lines l JOIN ledger_accounts a ON a.id=l.ledger_account_id "
                        + "WHERE l.journal_entry_id=? AND a.user_id=? ORDER BY l.id";
                try (PreparedStatement lineQuery = c.prepareStatement(lineSql)) {
                    lineQuery.setLong(1, id);
                    lineQuery.setLong(2, userId);
                    try (ResultSet lineRows = lineQuery.executeQuery()) {
                        while (lineRows.next()) {
                            Account account = new Account(lineRows.getLong(1), lineRows.getString(2),
                                    lineRows.getString(3), lineRows.getString(4));
                            lines.add(new JournalLine(account, lineRows.getBigDecimal(5), lineRows.getBigDecimal(6)));
                        }
                    }
                }
                return new JournalEntry(id, date, particulars, currency, entryType, lines);
            }
        }
    }

    List<JournalEntry> entries(long userId, String filter) throws SQLException {
        List<JournalEntry> entries = new ArrayList<>();
        String sql = "SELECT id,entry_date,particulars,currency,entry_type FROM journal_entries "
                + "WHERE user_id=? AND particulars LIKE ? ORDER BY entry_date DESC,id DESC";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId);
            p.setString(2, "%" + (filter == null ? "" : filter.trim()) + "%");
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    JournalEntry full = getEntry(userId, r.getLong(1));
                    if (full != null) entries.add(full);
                }
            }
        }
        return entries;
    }

    long saveEntry(long userId, JournalEntry entry) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                verifyOwnedAccounts(c, userId, entry.getLines());
                long id = entry.getId();
                if (id > 0) {
                    try (PreparedStatement p = c.prepareStatement(
                            "UPDATE journal_entries SET entry_date=?,particulars=?,currency=?,entry_type=? WHERE id=? AND user_id=?")) {
                        p.setDate(1, Date.valueOf(entry.getDate()));
                        p.setString(2, entry.getParticulars());
                        p.setString(3, entry.getCurrency());
                        p.setString(4, entry.getType());
                        p.setLong(5, id);
                        p.setLong(6, userId);
                        if (p.executeUpdate() != 1) throw new SQLException("Journal entry not found for this account.");
                    }
                    try (PreparedStatement p = c.prepareStatement(
                            "DELETE l FROM journal_entry_lines l JOIN journal_entries j ON j.id=l.journal_entry_id WHERE j.id=? AND j.user_id=?")) {
                        p.setLong(1, id);
                        p.setLong(2, userId);
                        p.executeUpdate();
                    }
                } else {
                    try (PreparedStatement p = c.prepareStatement(
                            "INSERT INTO journal_entries(user_id,entry_date,particulars,currency,entry_type) VALUES(?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        p.setLong(1, userId);
                        p.setDate(2, Date.valueOf(entry.getDate()));
                        p.setString(3, entry.getParticulars());
                        p.setString(4, entry.getCurrency());
                        p.setString(5, entry.getType());
                        p.executeUpdate();
                        try (ResultSet keys = p.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Could not create journal entry.");
                            id = keys.getLong(1);
                        }
                    }
                }
                insertLines(c, id, entry);
                c.commit();
                return id;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void verifyOwnedAccounts(Connection c, long userId, List<JournalLine> lines) throws SQLException {
        java.util.Set<Long> accountIds = new java.util.HashSet<>();
        for (JournalLine line : lines) accountIds.add(line.getAccount().getId());
        if (accountIds.isEmpty()) throw new SQLException("A journal entry must contain lines.");
        String placeholders = String.join(",", java.util.Collections.nCopies(accountIds.size(), "?"));
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM ledger_accounts WHERE user_id=? AND id IN (" + placeholders + ")")) {
            p.setLong(1, userId);
            int index = 2;
            for (Long accountId : accountIds) p.setLong(index++, accountId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || r.getInt(1) != accountIds.size()) {
                    throw new SQLException("Selected ledger accounts are not available for this user.");
                }
            }
        }
    }

    private void insertLines(Connection c, long entryId, JournalEntry entry) throws SQLException {
        String sql = "INSERT INTO journal_entry_lines(journal_entry_id,ledger_account_id,account_name,description,debit,credit) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (JournalLine line : entry.getLines()) {
                p.setLong(1, entryId);
                p.setLong(2, line.getAccount().getId());
                p.setString(3, line.getAccount().getName());
                p.setString(4, entry.getParticulars());
                p.setBigDecimal(5, line.getDebit());
                p.setBigDecimal(6, line.getCredit());
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    void deleteEntry(long userId, long entryId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("DELETE FROM journal_entries WHERE id=? AND user_id=?")) {
            p.setLong(1, entryId);
            p.setLong(2, userId);
            if (p.executeUpdate() != 1) throw new SQLException("Journal entry not found for this account.");
        }
    }

    void recordRecent(long userId, String itemType, long itemId, String itemName) throws SQLException {
        String safeName = itemName == null ? "" : itemName;
        if (safeName.length() > 200) safeName = safeName.substring(0, 200);
        try (Connection c = Database.getConnection()) {
            long recentId = 0;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT id FROM recent_views WHERE user_id=? AND item_type=? AND item_id<=>? "
                            + "ORDER BY viewed_at DESC,id DESC LIMIT 1")) {
                p.setLong(1, userId);
                p.setString(2, itemType);
                if (itemId > 0) p.setLong(3, itemId); else p.setNull(3, Types.BIGINT);
                try (ResultSet r = p.executeQuery()) { if (r.next()) recentId = r.getLong(1); }
            }
            if (recentId > 0) {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE recent_views SET item_name=?,viewed_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?")) {
                    p.setString(1, safeName);
                    p.setLong(2, recentId);
                    p.setLong(3, userId);
                    p.executeUpdate();
                }
            } else {
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO recent_views(user_id,item_type,item_id,item_name,viewed_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)")) {
                    p.setLong(1, userId);
                    p.setString(2, itemType);
                    if (itemId > 0) p.setLong(3, itemId); else p.setNull(3, Types.BIGINT);
                    p.setString(4, safeName);
                    p.executeUpdate();
                }
            }
        }
    }

    List<RecentView> recentViews(long userId) throws SQLException {
        List<RecentView> result = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT item_type,item_id,item_name,viewed_at FROM recent_views WHERE user_id=? ORDER BY viewed_at DESC,id DESC LIMIT 50")) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    long itemId = r.getLong(2);
                    if (r.wasNull()) itemId = 0;
                    result.add(new RecentView(r.getString(1), itemId, r.getString(3),
                            r.getTimestamp(4).toLocalDateTime()));
                }
            }
        }
        return result;
    }

    List<LedgerLine> ledger(long userId, long accountId) throws SQLException {
        String sql = "SELECT j.entry_date,j.particulars,j.currency,a.account_name,a.account_type,l.debit,l.credit "
                + "FROM journal_entry_lines l JOIN journal_entries j ON j.id=l.journal_entry_id "
                + "JOIN ledger_accounts a ON a.id=l.ledger_account_id "
                + "WHERE j.user_id=? AND a.user_id=? AND a.id=? ORDER BY j.entry_date,j.id,l.id";
        List<LedgerLine> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId);
            p.setLong(2, userId);
            p.setLong(3, accountId);
            try (ResultSet r = p.executeQuery()) {
                java.util.Map<String, java.math.BigDecimal> balances = new java.util.HashMap<>();
                while (r.next()) {
                    String type = r.getString(5);
                    String currency = r.getString(3);
                    java.math.BigDecimal debit = r.getBigDecimal(6);
                    java.math.BigDecimal credit = r.getBigDecimal(7);
                    boolean debitNormal = type.equals("ASSET") || type.equals("EXPENSE");
                    java.math.BigDecimal balance = balances.getOrDefault(currency, java.math.BigDecimal.ZERO);
                    balance = debitNormal ? balance.add(debit).subtract(credit) : balance.add(credit).subtract(debit);
                    balances.put(currency, balance);
                    result.add(new LedgerLine(r.getDate(1).toLocalDate(), r.getString(4), r.getString(2),
                            currency, debit, credit, balance));
                }
            }
        }
        return result;
    }

    List<WorksheetRow> worksheet(long userId, java.time.LocalDate from, java.time.LocalDate to) throws SQLException {
        String sql = "SELECT a.id,a.account_name,a.account_type,j.entry_type,j.currency,l.debit,l.credit "
                + "FROM ledger_accounts a LEFT JOIN journal_entries j ON j.user_id=a.user_id "
                + "AND (? IS NULL OR j.entry_date>=?) AND (? IS NULL OR j.entry_date<=?) "
                + "AND EXISTS (SELECT 1 FROM journal_entry_lines x "
                + "WHERE x.journal_entry_id=j.id AND x.ledger_account_id=a.id) "
                + "LEFT JOIN journal_entry_lines l ON l.journal_entry_id=j.id AND l.ledger_account_id=a.id "
                + "WHERE a.user_id=? ORDER BY a.account_code,l.id";
        List<WorksheetRow> result = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            if (from == null) { p.setNull(1, Types.DATE); p.setNull(2, Types.DATE); }
            else { p.setDate(1, Date.valueOf(from)); p.setDate(2, Date.valueOf(from)); }
            if (to == null) { p.setNull(3, Types.DATE); p.setNull(4, Types.DATE); }
            else { p.setDate(3, Date.valueOf(to)); p.setDate(4, Date.valueOf(to)); }
            p.setLong(5, userId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    result.add(new WorksheetRow(r.getLong(1), r.getString(2), r.getString(3),
                            r.getString(4), r.getString(5), r.getBigDecimal(6), r.getBigDecimal(7)));
                }
            }
        }
        return result;
    }

    static final class LedgerLine {
        final java.time.LocalDate date;
        final String account, explanation, currency;
        final java.math.BigDecimal debit, credit, balance;
        LedgerLine(java.time.LocalDate date, String account, String explanation, String currency,
                   java.math.BigDecimal debit, java.math.BigDecimal credit, java.math.BigDecimal balance) {
            this.date = date; this.account = account; this.explanation = explanation; this.currency = currency;
            this.debit = debit; this.credit = credit; this.balance = balance;
        }
    }

    static final class WorksheetRow {
        final long id;
        final String name, type, entryType, currency;
        final java.math.BigDecimal debit, credit;
        WorksheetRow(long id, String name, String type, String entryType, String currency,
                     java.math.BigDecimal debit, java.math.BigDecimal credit) {
            this.id=id; this.name=name; this.type=type; this.entryType=entryType; this.currency=currency;
            this.debit=debit; this.credit=credit;
        }
    }
}
