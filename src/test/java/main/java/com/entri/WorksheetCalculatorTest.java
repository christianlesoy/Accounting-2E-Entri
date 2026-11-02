package main.java.com.entri;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WorksheetCalculatorTest {
    public static void main(String[] args) {
        testBalancedWorksheetAndAdjustments();
        testNetLoss();
        testCurrenciesAreCheckedSeparately();
        testBalanceSheetClassificationsAndEquation();
        testBalanceSheetDifferenceAndContraAccounts();
        testBalanceSheetKeepsCurrenciesSeparateAndOmitsZeroRows();
        testBalanceSheetRejectsUnsupportedTypesAndEmptyInput();
        testBalanceSheetCsvRowsUseFreshDateAndCurrency();
        testWorksheetRejectsInvalidPeriodBeforeDatabaseAccess();
        testRecentReportDestinationCompatibility();
        System.out.println("WorksheetCalculatorTest passed.");
    }

    private static void testBalancedWorksheetAndAdjustments() {
        List<AccountingRepository.WorksheetRow> data = Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "PHP", "150", "0"),
                row(2, "Accounts Payable", "LIABILITY", "STANDARD", "PHP", "0", "50"),
                row(3, "Owner Capital", "EQUITY", "STANDARD", "PHP", "0", "50"),
                row(4, "Service Revenue", "REVENUE", "STANDARD", "PHP", "0", "80"),
                row(5, "Rent Expense", "EXPENSE", "STANDARD", "PHP", "30", "0"),
                row(5, "Rent Expense", "EXPENSE", "ADJUSTMENT", "PHP", "10", "0"),
                row(2, "Accounts Payable", "LIABILITY", "ADJUSTMENT", "PHP", "0", "10"));

        WorksheetCalculator.Result result = WorksheetCalculator.calculate(data);
        require(result.hasPostedEntries, "Posted journal entries should be detected.");
        require(result.balanced, "All worksheet groups should balance after adjustment: "
                + Arrays.toString(result.totalsByCurrency.get("PHP")));
        BigDecimal[] totals = result.totalsByCurrency.get("PHP");
        require(amount(totals[0], "180") && amount(totals[1], "180"),
                "Trial balance totals should be 180 on both sides.");
        require(amount(totals[2], "10") && amount(totals[3], "10"),
                "Adjustment totals should be 10 on both sides.");
        require(amount(totals[4], "190") && amount(totals[5], "190"),
                "Adjusted trial balance totals should be 190 on both sides.");
        require(amount(totals[6], "80") && amount(totals[7], "80"),
                "Income statement should balance after carrying 40 net income.");
        require(amount(totals[8], "0") && amount(totals[9], "90"),
                "Owner's equity should include capital and net income on the credit side.");
        require(amount(totals[10], "150") && amount(totals[11], "150"),
                "Balance sheet totals should include net income.");
        WorksheetCalculator.DisplayRow income = findRow(result.rows, "Net Income / (Loss) (PHP)");
        require(amount(income.values[6], "40") && amount(income.values[9], "40")
                        && amount(income.values[11], "40"),
                "Net income should appear in the income statement, owner's equity, and balance sheet.");
    }

    private static void testNetLoss() {
        List<AccountingRepository.WorksheetRow> data = Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "USD", "100", "0"),
                row(2, "Owner Capital", "EQUITY", "STANDARD", "USD", "0", "110"),
                row(3, "Service Revenue", "REVENUE", "STANDARD", "USD", "0", "20"),
                row(4, "Rent Expense", "EXPENSE", "STANDARD", "USD", "30", "0"));

        WorksheetCalculator.Result result = WorksheetCalculator.calculate(data);
        require(result.balanced, "A balanced trial balance with a net loss should remain balanced.");
        BigDecimal[] totals = result.totalsByCurrency.get("USD");
        require(amount(totals[6], "30") && amount(totals[7], "30"),
                "Income statement totals should reflect the loss.");
        require(amount(totals[8], "10") && amount(totals[9], "110"),
                "Net loss should reduce owner's equity.");
        require(amount(totals[10], "110") && amount(totals[11], "110"),
                "Net loss should be carried to the balance sheet.");
        WorksheetCalculator.DisplayRow loss = findRow(result.rows, "Net Income / (Loss) (USD)");
        require(amount(loss.values[7], "10") && amount(loss.values[8], "10")
                        && amount(loss.values[10], "10"),
                "Net loss should appear in the income statement, owner's equity, and balance sheet.");
    }

    private static void testCurrenciesAreCheckedSeparately() {
        List<AccountingRepository.WorksheetRow> data = Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "PHP", "100", "0"),
                row(2, "Owner Capital", "EQUITY", "STANDARD", "PHP", "0", "100"),
                row(3, "Cash", "ASSET", "STANDARD", "USD", "100", "0"),
                row(4, "Owner Capital", "EQUITY", "STANDARD", "USD", "0", "90"));

        WorksheetCalculator.Result result = WorksheetCalculator.calculate(data);
        require(result.totalsByCurrency.size() == 2, "Each currency should have separate worksheet totals.");
        require(!result.balanced, "An imbalance in either currency should mark the worksheet unbalanced.");
    }

    private static void testBalanceSheetClassificationsAndEquation() {
        BalanceSheetCalculator.Result result = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "USD", "150", "0"),
                row(2, "Accounts Payable", "LIABILITY", "STANDARD", "USD", "0", "40"),
                row(3, "Owner Capital", "EQUITY", "STANDARD", "USD", "0", "70"),
                row(4, "Service Revenue", "REVENUE", "STANDARD", "USD", "0", "60"),
                row(5, "Rent Expense", "EXPENSE", "ADJUSTMENT", "USD", "20", "0")));

        BalanceSheetCalculator.Totals totals = result.totalsByCurrency.get("USD");
        require(result.hasPostedEntries && amount(totals.assets, "150")
                        && amount(totals.liabilities, "40") && amount(totals.equity, "70"),
                "Asset, liability, and equity balances should use their signed classifications.");
        require(amount(totals.revenue, "60") && amount(totals.expenses, "20")
                        && amount(totals.netIncome, "40"),
                "Revenue less expenses should produce net income.");
        require(amount(totals.liabilitiesEquityAndEarnings, "150")
                        && amount(totals.difference, "0") && totals.isBalanced(),
                "Assets should equal liabilities plus equity plus current net income.");
        require(findBalanceSheetRow(result, "Cash").category.equals("Assets")
                        && findBalanceSheetRow(result, "Rent Expense").category.equals("Expenses")
                        && findBalanceSheetRow(result, "Service Revenue").category.equals("Revenue"),
                "Each nonzero account should be presented under its appropriate category.");
        require(findBalanceSheetRow(result, "Net Income / (Loss)").calculated,
                "Current net income should be presented as a calculated earnings row.");
    }

    private static void testBalanceSheetDifferenceAndContraAccounts() {
        BalanceSheetCalculator.Result assetsExceed = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "PHP", "130", "0"),
                row(2, "Accounts Payable", "LIABILITY", "STANDARD", "PHP", "0", "40"),
                row(3, "Capital", "EQUITY", "STANDARD", "PHP", "0", "30"),
                row(4, "Revenue", "REVENUE", "STANDARD", "PHP", "0", "10")));
        require(amount(assetsExceed.totalsByCurrency.get("PHP").difference, "50")
                        && !assetsExceed.totalsByCurrency.get("PHP").isBalanced(),
                "The difference should be assets less liabilities, equity, and earnings.");

        BalanceSheetCalculator.Result rightSideExceeds = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "PHP", "80", "0"),
                row(2, "Accounts Payable", "LIABILITY", "STANDARD", "PHP", "0", "50"),
                row(3, "Capital", "EQUITY", "STANDARD", "PHP", "0", "40"),
                row(4, "Revenue", "REVENUE", "STANDARD", "PHP", "0", "10")));
        require(amount(rightSideExceeds.totalsByCurrency.get("PHP").difference, "-20"),
                "Liabilities, equity, and earnings exceeding assets should produce a negative difference.");

        BalanceSheetCalculator.Result netLoss = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "USD", "100", "0"),
                row(2, "Owner Capital", "EQUITY", "STANDARD", "USD", "0", "110"),
                row(3, "Service Revenue", "REVENUE", "STANDARD", "USD", "0", "20"),
                row(4, "Rent Expense", "EXPENSE", "STANDARD", "USD", "30", "0")));
        BalanceSheetCalculator.Totals lossTotals = netLoss.totalsByCurrency.get("USD");
        require(amount(lossTotals.netIncome, "-10") && amount(lossTotals.difference, "0"),
                "A net loss should reduce the equation's right-hand side and remain signed negative.");

        BalanceSheetCalculator.Result contraBalances = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "USD", "100", "0"),
                row(2, "Accumulated Depreciation", "ASSET", "STANDARD", "USD", "0", "20"),
                row(3, "Owner Capital", "EQUITY", "STANDARD", "USD", "0", "10"),
                row(4, "Service Revenue", "REVENUE", "STANDARD", "USD", "0", "100"),
                row(5, "Sales Returns", "REVENUE", "STANDARD", "USD", "5", "0"),
                row(6, "Rent Expense", "EXPENSE", "STANDARD", "USD", "25", "0")));
        BalanceSheetCalculator.Totals contraTotals = contraBalances.totalsByCurrency.get("USD");
        require(amount(contraTotals.assets, "80")
                        && amount(contraTotals.revenue, "95")
                        && amount(contraTotals.netIncome, "70")
                        && amount(contraTotals.difference, "0"),
                "Credit asset and debit revenue balances should reduce their signed categories.");

        BalanceSheetCalculator.Result drawings = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "PHP", "85", "0"),
                row(2, "Owner Equity", "EQUITY", "STANDARD", "PHP", "15", "100")));
        require(amount(drawings.totalsByCurrency.get("PHP").equity, "85"),
                "A debit balance posted to an equity account should reduce equity.");
    }

    private static void testBalanceSheetKeepsCurrenciesSeparateAndOmitsZeroRows() {
        BalanceSheetCalculator.Result result = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "USD Cash", "ASSET", "STANDARD", "USD", "100", "0"),
                row(2, "USD Capital", "EQUITY", "STANDARD", "USD", "0", "100"),
                row(3, "PHP Cash", "ASSET", "STANDARD", "PHP", "100", "0"),
                row(4, "PHP Capital", "EQUITY", "STANDARD", "PHP", "0", "90"),
                row(5, "Zero-net Asset", "ASSET", "STANDARD", "USD", "10", "10")));

        require(result.totalsByCurrency.size() == 2,
                "Balance Sheet totals should remain separated by saved currency.");
        require(result.totalsByCurrency.get("USD").isBalanced()
                        && amount(result.totalsByCurrency.get("PHP").difference, "10"),
                "Each currency should have an independent balance comparison.");
        require(result.rows.stream().noneMatch(row -> row.name.equals("Zero-net Asset")),
                "Zero-net accounts should not appear as Balance Sheet rows.");
        require(result.rows.stream().allMatch(row -> row.currency.equals("USD")
                        || row.currency.equals("PHP")),
                "Displayed account rows should retain the saved currency code.");
    }

    private static void testBalanceSheetRejectsUnsupportedTypesAndEmptyInput() {
        boolean rejected = false;
        try {
            BalanceSheetCalculator.calculate(List.of(
                    row(1, "Unknown", "OTHER", null, null, null, null)));
        } catch (BalanceSheetCalculator.UnsupportedAccountTypeException ex) {
            rejected = ex.getMessage().contains("OTHER");
        }
        require(rejected, "Unsupported account types should stop calculation with a clear error.");

        BalanceSheetCalculator.Result noAccounts =
                BalanceSheetCalculator.calculate(Collections.emptyList());
        BalanceSheetCalculator.Result emptyAccounts = BalanceSheetCalculator.calculate(List.of(
                row(1, "Empty Account", "ASSET", null, null, null, null)));
        require(!noAccounts.hasPostedEntries && noAccounts.rows.isEmpty()
                        && noAccounts.totalsByCurrency.isEmpty()
                        && !emptyAccounts.hasPostedEntries && emptyAccounts.rows.isEmpty(),
                "No postings should produce an empty, non-balanced report result.");
    }

    private static void testBalanceSheetCsvRowsUseFreshDateAndCurrency() {
        LocalDate reportDate = LocalDate.of(2026, 10, 31);
        BalanceSheetCalculator.Result result = BalanceSheetCalculator.calculate(Arrays.asList(
                row(1, "Cash", "ASSET", "STANDARD", "USD", "100", "0"),
                row(2, "Capital", "EQUITY", "STANDARD", "USD", "0", "100"),
                row(3, "Peso Cash", "ASSET", "STANDARD", "PHP", "50", "0"),
                row(4, "Peso Capital", "EQUITY", "STANDARD", "PHP", "0", "50")));

        List<List<?>> rows = BalanceSheetCsv.rows(result, reportDate);
        require(BalanceSheetCsv.HEADERS.containsAll(List.of(
                        "Report Title", "Reporting Date", "Section", "Currency", "Amount")),
                "The Balance Sheet CSV should identify its report, date, sections, and currency.");
        require(rows.stream().allMatch(row -> row.get(0).equals("Balance Sheet")
                        && row.get(1).equals(reportDate)
                        && !row.get(2).toString().isBlank()
                        && !row.get(3).toString().isBlank()),
                "CSV rows should include the captured report date and exclude blank report rows.");
        require(rows.stream().anyMatch(row -> row.get(2).equals("Assets")
                        && row.get(5).equals("USD") && amount((BigDecimal) row.get(6), "100"))
                        && rows.stream().anyMatch(row -> row.get(2).equals("Assets")
                        && row.get(5).equals("PHP") && amount((BigDecimal) row.get(6), "50")),
                "CSV category totals should preserve each saved currency and amount.");

        BalanceSheetCalculator.Result empty =
                BalanceSheetCalculator.calculate(Collections.emptyList());
        require(BalanceSheetCsv.rows(empty, reportDate).isEmpty(),
                "A successfully loaded report with no exportable rows should remain empty.");
    }

    private static void testWorksheetRejectsInvalidPeriodBeforeDatabaseAccess() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        boolean rejected = false;
        try {
            new AccountingService().worksheet(1, start.plusDays(1), start);
        } catch (IllegalArgumentException ex) {
            rejected = true;
        } catch (java.sql.SQLException ex) {
            throw new AssertionError("Invalid dates must be rejected before database access.", ex);
        }
        require(rejected, "The service should reject a worksheet period with reversed dates.");
    }

    private static void testRecentReportDestinationCompatibility() {
        LocalDateTime viewedAt = LocalDateTime.of(2026, 11, 2, 20, 0);
        require(new RecentView("WORKSHEET", 2, "Balance Sheet", viewedAt)
                        .isBalanceSheetReport(),
                "A saved Balance Sheet view should retain its destination.");
        require(!new RecentView("WORKSHEET", 1, "12-Column Worksheet", viewedAt)
                        .isBalanceSheetReport()
                        && !new RecentView("WORKSHEET", 0, "Old Worksheet", viewedAt)
                        .isBalanceSheetReport(),
                "Worksheet and legacy recent records should continue opening as worksheets.");
    }

    private static BalanceSheetCalculator.AccountRow findBalanceSheetRow(
            BalanceSheetCalculator.Result result, String name) {
        for (BalanceSheetCalculator.AccountRow row : result.rows) {
            if (row.name.equals(name)) return row;
        }
        throw new AssertionError("Could not find Balance Sheet row: " + name);
    }

    private static AccountingRepository.WorksheetRow row(long id, String name, String type,
                                                           String entryType, String currency,
                                                           String debit, String credit) {
        return new AccountingRepository.WorksheetRow(id, name, type, entryType, currency,
                decimal(debit), decimal(credit));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static WorksheetCalculator.DisplayRow findRow(
            List<WorksheetCalculator.DisplayRow> rows, String name) {
        for (WorksheetCalculator.DisplayRow row : rows) {
            if (row.name.equals(name)) return row;
        }
        throw new AssertionError("Could not find worksheet row: " + name);
    }

    private static boolean amount(BigDecimal actual, String expected) {
        return actual != null && actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
