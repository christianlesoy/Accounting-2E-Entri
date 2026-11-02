package main.java.com.entri;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatter {
    private CurrencyFormatter() {
    }

    public static boolean isSupported(String currencyCode) {
        if (currencyCode == null) {
            return false;
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        return "PHP".equals(normalized) || "USD".equals(normalized);
    }

    public static String symbolFor(String currencyCode) {
        String normalized = currencyCode == null ? "USD" : currencyCode.trim().toUpperCase(Locale.ROOT);
        if ("PHP".equals(normalized)) {
            return "\u20b1";
        }
        return "$";
    }

    public static String format(BigDecimal amount, String currencyCode) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        String normalized = isSupported(currencyCode) ? currencyCode.trim().toUpperCase(Locale.ROOT) : "USD";
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        return symbolFor(normalized) + numberFormat.format(safeAmount);
    }
}
