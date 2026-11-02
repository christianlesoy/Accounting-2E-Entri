package main.java.com.entri;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class UserSettingsRepository {
    String getCurrency(long userId) throws SQLException {
        String sql = "SELECT currency_code FROM user_settings WHERE user_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String currency = result.getString("currency_code");
                    if (CurrencyFormatter.isSupported(currency)) {
                        return currency;
                    }
                }
            }
        }
        return "USD";
    }

    void saveCurrency(long userId, String currencyCode) throws SQLException {
        if (!CurrencyFormatter.isSupported(currencyCode)) {
            throw new IllegalArgumentException("Choose PHP or USD.");
        }

        String sql = "INSERT INTO user_settings(user_id,currency_code) VALUES(?,?) "
                + "ON CONFLICT (user_id) DO UPDATE SET currency_code=EXCLUDED.currency_code";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, currencyCode);
            statement.executeUpdate();
        }
    }
}
