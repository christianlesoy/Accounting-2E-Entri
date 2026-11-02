package main.java.com.entri;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class WorksheetCalculator {
    private WorksheetCalculator() {}

    static Result calculate(List<AccountingRepository.WorksheetRow> data) {
        if (data == null) {
            data = java.util.Collections.emptyList();
        }
        boolean hasPostedEntries = false;
        Map<String, Aggregate> accounts = new LinkedHashMap<>();
        for (AccountingRepository.WorksheetRow row : data) {
            if (row.entryType != null && row.debit != null && row.credit != null) {
                hasPostedEntries = true;
            }
            String currency = row.currency == null ? "—" : row.currency;
            String key = row.id + ":" + currency;
            Aggregate account = accounts.get(key);
            if (account == null) {
                account = new Aggregate(row.name + " (" + currency + ")", row.type, currency);
                accounts.put(key, account);
            }
            if (row.entryType == null || row.debit == null || row.credit == null) continue;
            if ("ADJUSTMENT".equals(row.entryType)) {
                account.adjustDebit = account.adjustDebit.add(row.debit);
                account.adjustCredit = account.adjustCredit.add(row.credit);
            } else {
                account.trialDebit = account.trialDebit.add(row.debit);
                account.trialCredit = account.trialCredit.add(row.credit);
            }
        }

        List<DisplayRow> rows = new ArrayList<>();
        Map<String, BigDecimal[]> totalsByCurrency = new TreeMap<>();
        Map<String, BigDecimal> netIncomeByCurrency = new TreeMap<>();
        for (Aggregate account : accounts.values()) {
            BigDecimal[] sums = totalsByCurrency.get(account.currency);
            if (sums == null) {
                sums = new BigDecimal[12];
                Arrays.fill(sums, BigDecimal.ZERO);
                totalsByCurrency.put(account.currency, sums);
            }
            BigDecimal trialNet = account.trialDebit.subtract(account.trialCredit);
            BigDecimal adjustmentNet = account.adjustDebit.subtract(account.adjustCredit);
            BigDecimal adjustedNet = trialNet.add(adjustmentNet);
            DisplayRow row = new DisplayRow(account.name, account.currency);
            putNet(row.values, 0, trialNet, isDebitNormal(account.type));
            row.values[2] = account.adjustDebit;
            row.values[3] = account.adjustCredit;
            putNet(row.values, 4, adjustedNet, isDebitNormal(account.type));
            if ("REVENUE".equals(account.type) || "EXPENSE".equals(account.type)) {
                putNet(row.values, 6, adjustedNet, isDebitNormal(account.type));
                BigDecimal currentIncome = netIncomeByCurrency.get(account.currency);
                if (currentIncome == null) currentIncome = BigDecimal.ZERO;
                netIncomeByCurrency.put(account.currency, currentIncome.add(adjustedNet.negate()));
            }
            if ("EQUITY".equals(account.type)) putNet(row.values, 8, adjustedNet, false);
            if ("ASSET".equals(account.type) || "LIABILITY".equals(account.type)
                    || "EQUITY".equals(account.type)) {
                putNet(row.values, 10, adjustedNet, isDebitNormal(account.type));
            }
            addValues(sums, row.values);
            rows.add(row);
        }

        for (Map.Entry<String, BigDecimal> incomeEntry : netIncomeByCurrency.entrySet()) {
            String currency = incomeEntry.getKey();
            BigDecimal netIncome = incomeEntry.getValue();
            DisplayRow incomeRow = new DisplayRow("Net Income / (Loss) (" + currency + ")", currency);
            if (netIncome.signum() > 0) {
                incomeRow.values[6] = netIncome;
                incomeRow.values[9] = netIncome;
                incomeRow.values[11] = netIncome;
            } else if (netIncome.signum() < 0) {
                BigDecimal loss = netIncome.negate();
                incomeRow.values[7] = loss;
                incomeRow.values[8] = loss;
                incomeRow.values[10] = loss;
            }
            addValues(totalsByCurrency.get(currency), incomeRow.values);
            rows.add(incomeRow);
        }

        boolean balanced = true;
        for (BigDecimal[] sums : totalsByCurrency.values()) {
            if (sums[0].compareTo(sums[1]) != 0
                    || sums[2].compareTo(sums[3]) != 0
                    || sums[4].compareTo(sums[5]) != 0
                    || sums[6].compareTo(sums[7]) != 0
                    || sums[10].compareTo(sums[11]) != 0) {
                balanced = false;
                break;
            }
        }
        return new Result(rows, totalsByCurrency, hasPostedEntries, balanced);
    }

    private static void addValues(BigDecimal[] totals, BigDecimal[] values) {
        for (int i = 0; i < values.length; i++) totals[i] = totals[i].add(values[i]);
    }

    private static boolean isDebitNormal(String type) {
        return "ASSET".equals(type) || "EXPENSE".equals(type);
    }

    private static void putNet(BigDecimal[] values, int index, BigDecimal net, boolean debitNormal) {
        BigDecimal normal = debitNormal ? net : net.negate();
        if (normal.signum() > 0) {
            if (debitNormal) values[index] = normal;
            else values[index + 1] = normal;
        } else if (normal.signum() < 0) {
            if (debitNormal) values[index + 1] = normal.negate();
            else values[index] = normal.negate();
        }
    }

    static final class Result {
        final List<DisplayRow> rows;
        final Map<String, BigDecimal[]> totalsByCurrency;
        final boolean hasPostedEntries;
        final boolean balanced;

        Result(List<DisplayRow> rows, Map<String, BigDecimal[]> totalsByCurrency,
               boolean hasPostedEntries, boolean balanced) {
            this.rows = rows;
            this.totalsByCurrency = totalsByCurrency;
            this.hasPostedEntries = hasPostedEntries;
            this.balanced = balanced;
        }
    }

    static final class DisplayRow {
        final String name;
        final String currency;
        final BigDecimal[] values = new BigDecimal[12];
        DisplayRow(String name, String currency) {
            this.name = name;
            this.currency = currency;
            Arrays.fill(values, BigDecimal.ZERO);
        }
    }

    private static final class Aggregate {
        final String name;
        final String type;
        final String currency;
        BigDecimal trialDebit = BigDecimal.ZERO;
        BigDecimal trialCredit = BigDecimal.ZERO;
        BigDecimal adjustDebit = BigDecimal.ZERO;
        BigDecimal adjustCredit = BigDecimal.ZERO;

        Aggregate(String name, String type, String currency) {
            this.name = name;
            this.type = type;
            this.currency = currency;
        }
    }
}
