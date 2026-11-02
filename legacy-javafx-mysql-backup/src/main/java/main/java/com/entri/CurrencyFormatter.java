package main.java.com.entri;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatter {
    private CurrencyFormatter() {
    }

    public static boolean isSupported(String currencyCode) {
        return "PHP".equals(currencyCode) || "USD".equals(currencyCode);
    }

    public static String symbolFor(String currencyCode) {
        if ("PHP".equals(currencyCode)) {
            return "\u20b1";
        }
        return "$";
    }

    public static String format(BigDecimal amount, String currencyCode) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        return symbolFor(currencyCode) + numberFormat.format(amount);
    }
}
