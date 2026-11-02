package main.java.com.entri;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class LedgerCalculatorTest {
    public static void main(String[] args) {
        testInclusiveDateRangeAndChronologicalRunningBalance();
        testOpenEndedDateRangesAndInvalidRangeValidation();
        testCurrenciesAreCalculatedSeparately();
        testOpeningBalanceIsRetainedForAnEmptyPeriod();
        testBalanceSidesAndNormalSideClassification();
        System.out.println("LedgerCalculatorTest passed.");
    }

    private static void testInclusiveDateRangeAndChronologicalRunningBalance() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 20);
        List<AccountingRepository.LedgerLine> postings = Arrays.asList(
                line(30, 1, end.plusDays(1), "PHP", "0", "50"),
                line(20, 2, start, "PHP", "3", "0"),
                line(5, 1, start.minusDays(1), "PHP", "10.00", "2.0"),
                line(10, 9, start, "PHP", "0", "4"),
                line(25, 1, end, "PHP", "2", "0"));

        LedgerCalculator.Result result = LedgerCalculator.calculate(postings, start, end);
        require(result.periodRows.size() == 3, "Start/end dates should be inclusive, excluding later postings.");
        require(result.periodRows.get(0).entryId == 10 && result.periodRows.get(1).entryId == 20,
                "Same-day postings should sort by entry ID and line ID.");
        require(amount(result.openingByCurrency.get("PHP"), "8"),
                "Opening balance should include only postings before the start date.");
        require(amount(result.debitsByCurrency.get("PHP"), "5")
                        && amount(result.creditsByCurrency.get("PHP"), "4"),
                "Period debit and credit totals should include all period lines.");
        require(amount(result.periodRows.get(0).balance, "4")
                        && amount(result.periodRows.get(1).balance, "7")
                        && amount(result.periodRows.get(2).balance, "9"),
                "Running balances should carry the opening amount in chronological order.");
        require(amount(result.closingByCurrency.get("PHP"), "9"),
                "Closing balance should equal opening balance plus period debits less credits.");
    }

    private static void testCurrenciesAreCalculatedSeparately() {
        List<AccountingRepository.LedgerLine> postings = Arrays.asList(
                line(1, 1, LocalDate.of(2026, 2, 1), "PHP", "100", "100"),
                line(2, 1, LocalDate.of(2026, 2, 2), "USD", "5", "4"));

        LedgerCalculator.Result result = LedgerCalculator.calculate(postings, null, null);
        require(result.closingByCurrency.size() == 2, "Each saved currency should have its own balance.");
        require(amount(result.closingByCurrency.get("PHP"), "0")
                        && amount(result.closingByCurrency.get("USD"), "1"),
                "Amounts from different currencies must not be combined.");
        require(result.periodRows.get(0).currency.equals("PHP")
                        && result.periodRows.get(0).balance.compareTo(BigDecimal.ZERO) == 0,
                "A zero running balance should remain zero in its own currency.");
    }

    private static void testOpenEndedDateRangesAndInvalidRangeValidation() {
        LocalDate middle = LocalDate.of(2026, 2, 15);
        List<AccountingRepository.LedgerLine> postings = Arrays.asList(
                line(1, 1, middle.minusDays(1), "USD", "10", "0"),
                line(2, 1, middle, "USD", "2", "0"),
                line(3, 1, middle.plusDays(1), "USD", "3", "0"));

        LedgerCalculator.Result startOnly = LedgerCalculator.calculate(postings, middle, null);
        require(amount(startOnly.openingByCurrency.get("USD"), "10")
                        && amount(startOnly.debitsByCurrency.get("USD"), "5")
                        && amount(startOnly.closingByCurrency.get("USD"), "15"),
                "A start-only range should carry history forward and include later postings.");

        LedgerCalculator.Result endOnly = LedgerCalculator.calculate(postings, null, middle);
        require(amount(endOnly.openingByCurrency.get("USD"), "0")
                        && amount(endOnly.debitsByCurrency.get("USD"), "12")
                        && amount(endOnly.closingByCurrency.get("USD"), "12"),
                "An end-only range should include postings through the end date from a zero opening balance.");

        boolean rejected = false;
        try {
            new AccountingService().ledgerPeriod(1, 1, middle.plusDays(1), middle);
        } catch (IllegalArgumentException ex) {
            rejected = true;
        } catch (SQLException ex) {
            throw new AssertionError("Invalid dates should be rejected before database access.", ex);
        }
        require(rejected, "The service should reject a start date later than the end date.");
    }

    private static void testOpeningBalanceIsRetainedForAnEmptyPeriod() {
        LocalDate start = LocalDate.of(2026, 3, 10);
        List<AccountingRepository.LedgerLine> postings = List.of(
                line(1, 1, start.minusDays(1), "EUR", "0", "12.50"));

        LedgerCalculator.Result result = LedgerCalculator.calculate(
                postings, start, start.plusDays(2));
        require(result.periodRows.isEmpty(), "An interval without postings should have no period rows.");
        require(amount(result.openingByCurrency.get("EUR"), "-12.50")
                        && amount(result.closingByCurrency.get("EUR"), "-12.50"),
                "An empty period should carry its historical opening amount into the closing balance.");
        require(amount(result.debitsByCurrency.get("EUR"), "0")
                        && amount(result.creditsByCurrency.get("EUR"), "0"),
                "An empty period should report zero period debits and credits.");
    }

    private static void testBalanceSidesAndNormalSideClassification() {
        require("ZERO".equals(LedgerCalculator.actualSide(new BigDecimal("0.00"))),
                "A numeric zero balance should be classified explicitly as zero.");
        require("CREDIT".equals(LedgerCalculator.actualSide(new BigDecimal("-0.01"))),
                "Negative debit-minus-credit balances should be credit-side balances.");
        require("DEBIT".equals(LedgerCalculator.expectedNormalSide("ASSET"))
                        && "DEBIT".equals(LedgerCalculator.expectedNormalSide("EXPENSE"))
                        && "CREDIT".equals(LedgerCalculator.expectedNormalSide("LIABILITY"))
                        && "CREDIT".equals(LedgerCalculator.expectedNormalSide("EQUITY"))
                        && "CREDIT".equals(LedgerCalculator.expectedNormalSide("REVENUE"))
                        && "UNSPECIFIED".equals(LedgerCalculator.expectedNormalSide("OTHER")),
                "Normal-side display should follow the supported broad account classifications.");
    }

    private static AccountingRepository.LedgerLine line(long entryId, long lineId,
                                                         LocalDate date, String currency,
                                                         String debit, String credit) {
        return new AccountingRepository.LedgerLine(entryId, lineId, date, "Test posting",
                currency, "1000", "Test account", "ASSET",
                new BigDecimal(debit), new BigDecimal(credit), BigDecimal.ZERO);
    }

    private static boolean amount(BigDecimal actual, String expected) {
        return actual != null && actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
