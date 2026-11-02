package main.java.com.entri;

import java.math.BigDecimal;

public final class CurrencyFormatterTest {
    public static void main(String[] args) {
        require("₱".equals(CurrencyFormatter.symbolFor("PHP")), "PHP must use the peso symbol.");
        require("$".equals(CurrencyFormatter.symbolFor("USD")), "USD must use the dollar symbol.");
        require("₱1,250.00".equals(CurrencyFormatter.format(
                        new BigDecimal("1250"), "PHP")),
                "PHP values must include commas and two decimal places.");
        require("$1,250.50".equals(CurrencyFormatter.format(
                        new BigDecimal("1250.5"), "USD")),
                "USD values must include commas and two decimal places.");
        require(CurrencyFormatter.isSupported("PHP"), "PHP must be supported.");
        require(CurrencyFormatter.isSupported("USD"), "USD must be supported.");
        require(!CurrencyFormatter.isSupported("EUR"), "Other currencies must not be supported.");
        System.out.println("CurrencyFormatterTest passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
