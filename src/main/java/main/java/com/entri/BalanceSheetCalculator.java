package main.java.com.entri;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class BalanceSheetCalculator {
    private BalanceSheetCalculator() {
    }

    static Result calculate(List<AccountingRepository.WorksheetRow> data) {
        List<AccountingRepository.WorksheetRow> rows =
                data == null ? Collections.emptyList() : data;
        Map<String, AccountBalance> accounts = new LinkedHashMap<>();
        Map<String, MutableTotals> totalsByCurrency = new TreeMap<>();
        boolean hasPostedEntries = false;

        for (AccountingRepository.WorksheetRow row : rows) {
            validateAccountType(row.type);
            if (row.entryType == null) continue;
            if (row.debit == null || row.credit == null) {
                throw new IllegalStateException("A saved journal line has a missing debit or credit amount.");
            }
            if (row.currency == null || row.currency.isBlank()) {
                throw new IllegalStateException("A saved journal line has no currency code.");
            }

            hasPostedEntries = true;
            String currency = row.currency.trim().toUpperCase(java.util.Locale.ROOT);
            String key = row.id + ":" + currency;
            AccountBalance account = accounts.computeIfAbsent(key,
                    ignored -> new AccountBalance(row.id, row.name, row.type, currency));
            account.debit = account.debit.add(row.debit);
            account.credit = account.credit.add(row.credit);
            totalsByCurrency.computeIfAbsent(currency, ignored -> new MutableTotals());
        }

        List<AccountRow> accountRows = new ArrayList<>();
        for (AccountBalance account : accounts.values()) {
            BigDecimal netDebit = account.debit.subtract(account.credit);
            MutableTotals totals = totalsByCurrency.get(account.currency);
            String category;
            BigDecimal amount;
            switch (account.type) {
                case "ASSET":
                    category = "Assets";
                    amount = netDebit;
                    totals.assets = totals.assets.add(amount);
                    break;
                case "LIABILITY":
                    category = "Liabilities";
                    amount = netDebit.negate();
                    totals.liabilities = totals.liabilities.add(amount);
                    break;
                case "EQUITY":
                    category = "Owner's Equity";
                    amount = netDebit.negate();
                    totals.equity = totals.equity.add(amount);
                    break;
                case "REVENUE":
                    category = "Revenue";
                    amount = netDebit.negate();
                    totals.revenue = totals.revenue.add(amount);
                    break;
                case "EXPENSE":
                    category = "Expenses";
                    amount = netDebit;
                    totals.expenses = totals.expenses.add(amount);
                    break;
                default:
                    throw unsupportedAccountType(account.type);
            }
            if (amount.signum() != 0) {
                accountRows.add(new AccountRow(account.name, account.type, category,
                        account.currency, amount, false));
            }
        }

        for (Map.Entry<String, MutableTotals> entry : totalsByCurrency.entrySet()) {
            MutableTotals totals = entry.getValue();
            totals.netIncome = totals.revenue.subtract(totals.expenses);
            totals.liabilitiesEquityAndEarnings =
                    totals.liabilities.add(totals.equity).add(totals.netIncome);
            totals.difference = totals.assets.subtract(totals.liabilitiesEquityAndEarnings);
            if (totals.netIncome.signum() != 0) {
                accountRows.add(new AccountRow("Net Income / (Loss)", "Calculated Earnings",
                        "Earnings", entry.getKey(), totals.netIncome, true));
            }
        }

        Map<String, Totals> immutableTotals = new TreeMap<>();
        for (Map.Entry<String, MutableTotals> entry : totalsByCurrency.entrySet()) {
            MutableTotals totals = entry.getValue();
            immutableTotals.put(entry.getKey(), new Totals(totals.assets, totals.liabilities,
                    totals.equity, totals.revenue, totals.expenses, totals.netIncome,
                    totals.liabilitiesEquityAndEarnings, totals.difference));
        }
        return new Result(accountRows, immutableTotals, hasPostedEntries);
    }

    private static void validateAccountType(String accountType) {
        if (!"ASSET".equals(accountType) && !"LIABILITY".equals(accountType)
                && !"EQUITY".equals(accountType) && !"REVENUE".equals(accountType)
                && !"EXPENSE".equals(accountType)) {
            throw unsupportedAccountType(accountType);
        }
    }

    private static UnsupportedAccountTypeException unsupportedAccountType(String accountType) {
        String displayType = accountType == null || accountType.isBlank()
                ? "(blank)" : accountType;
        return new UnsupportedAccountTypeException(
                "Unsupported account classification: " + displayType + ".");
    }

    static final class Result {
        final List<AccountRow> rows;
        final Map<String, Totals> totalsByCurrency;
        final boolean hasPostedEntries;

        private Result(List<AccountRow> rows, Map<String, Totals> totalsByCurrency,
                       boolean hasPostedEntries) {
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.totalsByCurrency = Collections.unmodifiableMap(new TreeMap<>(totalsByCurrency));
            this.hasPostedEntries = hasPostedEntries;
        }
    }

    static final class AccountRow {
        final String name;
        final String accountType;
        final String category;
        final String currency;
        final BigDecimal amount;
        final boolean calculated;

        private AccountRow(String name, String accountType, String category,
                           String currency, BigDecimal amount, boolean calculated) {
            this.name = name;
            this.accountType = accountType;
            this.category = category;
            this.currency = currency;
            this.amount = amount;
            this.calculated = calculated;
        }
    }

    static final class Totals {
        final BigDecimal assets;
        final BigDecimal liabilities;
        final BigDecimal equity;
        final BigDecimal revenue;
        final BigDecimal expenses;
        final BigDecimal netIncome;
        final BigDecimal liabilitiesEquityAndEarnings;
        final BigDecimal difference;

        private Totals(BigDecimal assets, BigDecimal liabilities, BigDecimal equity,
                       BigDecimal revenue, BigDecimal expenses, BigDecimal netIncome,
                       BigDecimal liabilitiesEquityAndEarnings, BigDecimal difference) {
            this.assets = assets;
            this.liabilities = liabilities;
            this.equity = equity;
            this.revenue = revenue;
            this.expenses = expenses;
            this.netIncome = netIncome;
            this.liabilitiesEquityAndEarnings = liabilitiesEquityAndEarnings;
            this.difference = difference;
        }

        boolean isBalanced() {
            return difference.compareTo(BigDecimal.ZERO) == 0;
        }
    }

    static final class UnsupportedAccountTypeException extends IllegalArgumentException {
        private UnsupportedAccountTypeException(String message) {
            super(message);
        }
    }

    private static final class AccountBalance {
        final long id;
        final String name;
        final String type;
        final String currency;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;

        AccountBalance(long id, String name, String type, String currency) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.currency = currency;
        }
    }

    private static final class MutableTotals {
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal netIncome = BigDecimal.ZERO;
        BigDecimal liabilitiesEquityAndEarnings = BigDecimal.ZERO;
        BigDecimal difference = BigDecimal.ZERO;
    }
}
