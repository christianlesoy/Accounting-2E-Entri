package main.java.com.entri;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BalanceSheetCsv {
    static final List<String> HEADERS = List.of(
            "Report Title", "Reporting Date", "Section", "Account",
            "Account Type", "Currency", "Amount");

    private BalanceSheetCsv() {
    }

    static List<List<?>> rows(BalanceSheetCalculator.Result result, LocalDate reportDate) {
        if (result == null || reportDate == null) {
            throw new IllegalArgumentException("A loaded report and its reporting date are required.");
        }
        List<List<?>> rows = new ArrayList<>();
        for (BalanceSheetCalculator.AccountRow row : result.rows) {
            if (isBlank(row.name) || isBlank(row.category) || isBlank(row.accountType)
                    || isBlank(row.currency) || row.amount == null) {
                continue;
            }
            rows.add(List.of("Balance Sheet", reportDate, row.category, row.name,
                    row.accountType, row.currency, row.amount));
        }
        for (Map.Entry<String, BalanceSheetCalculator.Totals> entry
                : result.totalsByCurrency.entrySet()) {
            String currency = entry.getKey();
            BalanceSheetCalculator.Totals totals = entry.getValue();
            addTotal(rows, reportDate, "Assets", currency, totals.assets);
            addTotal(rows, reportDate, "Liabilities", currency, totals.liabilities);
            addTotal(rows, reportDate, "Owner's Equity", currency, totals.equity);
            addTotal(rows, reportDate, "Revenue", currency, totals.revenue);
            addTotal(rows, reportDate, "Expenses", currency, totals.expenses);
            addTotal(rows, reportDate, "Net Income / (Loss)", currency, totals.netIncome);
            addTotal(rows, reportDate, "Liabilities + Equity + Earnings", currency,
                    totals.liabilitiesEquityAndEarnings);
            addTotal(rows, reportDate, "Difference", currency, totals.difference);
        }
        return rows;
    }

    private static void addTotal(List<List<?>> rows, LocalDate reportDate, String section,
                                 String currency, java.math.BigDecimal amount) {
        if (!isBlank(currency) && amount != null) {
            rows.add(List.of("Balance Sheet", reportDate, section, "TOTAL",
                    "", currency, amount));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
