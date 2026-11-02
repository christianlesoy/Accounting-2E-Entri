package main.java.com.entri;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class JournalTotalsByCurrency {
    private JournalTotalsByCurrency() {
    }

    static Map<String, CurrencyTotals> calculate(List<JournalEntry> entries) {
        Map<String, BigDecimal[]> sums = new TreeMap<>();
        for (JournalEntry entry : entries) {
            BigDecimal[] totals = sums.computeIfAbsent(entry.getCurrency(),
                    ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(entry.getDebitTotal());
            totals[1] = totals[1].add(entry.getCreditTotal());
        }

        Map<String, CurrencyTotals> result = new TreeMap<>();
        for (Map.Entry<String, BigDecimal[]> entry : sums.entrySet()) {
            result.put(entry.getKey(), new CurrencyTotals(entry.getValue()[0], entry.getValue()[1]));
        }
        return Collections.unmodifiableMap(result);
    }

    static final class CurrencyTotals {
        private final BigDecimal debit;
        private final BigDecimal credit;

        private CurrencyTotals(BigDecimal debit, BigDecimal credit) {
            this.debit = debit;
            this.credit = credit;
        }

        BigDecimal debit() { return debit; }
        BigDecimal credit() { return credit; }
        boolean isBalanced() { return debit.compareTo(credit) == 0; }
    }
}
