package main.java.com.entri;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class LedgerCalculator {
    private LedgerCalculator() {
    }

    static Result calculate(List<AccountingRepository.LedgerLine> postings,
                            LocalDate startDate, LocalDate endDate) {
        List<AccountingRepository.LedgerLine> sorted = new ArrayList<>(postings);
        sorted.sort(Comparator.comparing((AccountingRepository.LedgerLine line) -> line.date)
                .thenComparingLong(line -> line.entryId)
                .thenComparingLong(line -> line.lineId));

        Map<String, BigDecimal> opening = new TreeMap<>();
        Map<String, BigDecimal> balances = new TreeMap<>();
        Map<String, BigDecimal> debits = new TreeMap<>();
        Map<String, BigDecimal> credits = new TreeMap<>();
        List<AccountingRepository.LedgerLine> periodRows = new ArrayList<>();

        for (AccountingRepository.LedgerLine line : sorted) {
            if (endDate != null && line.date.isAfter(endDate)) continue;
            if (startDate != null && line.date.isBefore(startDate)) {
                opening.merge(line.currency, line.debit.subtract(line.credit), BigDecimal::add);
                continue;
            }
            balances.putIfAbsent(line.currency, opening.getOrDefault(line.currency, BigDecimal.ZERO));
            debits.merge(line.currency, line.debit, BigDecimal::add);
            credits.merge(line.currency, line.credit, BigDecimal::add);
            BigDecimal balance = balances.get(line.currency)
                    .add(line.debit).subtract(line.credit);
            balances.put(line.currency, balance);
            periodRows.add(line.withBalance(balance));
        }

        for (String currency : balances.keySet()) {
            opening.putIfAbsent(currency, BigDecimal.ZERO);
        }
        Map<String, BigDecimal> closing = new TreeMap<>(opening);
        for (Map.Entry<String, BigDecimal> balance : balances.entrySet()) {
            closing.put(balance.getKey(), balance.getValue());
        }
        for (String currency : closing.keySet()) {
            debits.putIfAbsent(currency, BigDecimal.ZERO);
            credits.putIfAbsent(currency, BigDecimal.ZERO);
        }

        return new Result(periodRows, opening, debits, credits, closing);
    }

    static String expectedNormalSide(String accountType) {
        if ("ASSET".equals(accountType) || "EXPENSE".equals(accountType)) return "DEBIT";
        if ("LIABILITY".equals(accountType) || "EQUITY".equals(accountType)
                || "REVENUE".equals(accountType)) return "CREDIT";
        return "UNSPECIFIED";
    }

    static String actualSide(BigDecimal balance) {
        if (balance.signum() > 0) return "DEBIT";
        if (balance.signum() < 0) return "CREDIT";
        return "ZERO";
    }

    static final class Result {
        final List<AccountingRepository.LedgerLine> periodRows;
        final Map<String, BigDecimal> openingByCurrency;
        final Map<String, BigDecimal> debitsByCurrency;
        final Map<String, BigDecimal> creditsByCurrency;
        final Map<String, BigDecimal> closingByCurrency;

        private Result(List<AccountingRepository.LedgerLine> periodRows,
                       Map<String, BigDecimal> openingByCurrency,
                       Map<String, BigDecimal> debitsByCurrency,
                       Map<String, BigDecimal> creditsByCurrency,
                       Map<String, BigDecimal> closingByCurrency) {
            this.periodRows = Collections.unmodifiableList(new ArrayList<>(periodRows));
            this.openingByCurrency = immutableSortedMap(openingByCurrency);
            this.debitsByCurrency = immutableSortedMap(debitsByCurrency);
            this.creditsByCurrency = immutableSortedMap(creditsByCurrency);
            this.closingByCurrency = immutableSortedMap(closingByCurrency);
        }

        private static Map<String, BigDecimal> immutableSortedMap(Map<String, BigDecimal> source) {
            return Collections.unmodifiableMap(new TreeMap<>(source));
        }
    }
}
