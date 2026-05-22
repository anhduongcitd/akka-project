package com.example.payment.domain;

/**
 * Supported currencies for payment transactions.
 * Aligned with FR-018: Multi-currency support (USD, EUR, GBP, JPY, AUD).
 */
public enum Currency {
    USD("US Dollar", "$", 2),
    EUR("Euro", "€", 2),
    GBP("British Pound", "£", 2),
    JPY("Japanese Yen", "¥", 0),
    AUD("Australian Dollar", "A$", 2);

    private final String displayName;
    private final String symbol;
    private final int decimalPlaces;

    Currency(String displayName, String symbol, int decimalPlaces) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
    }

    public String displayName() {
        return displayName;
    }

    public String symbol() {
        return symbol;
    }

    public int decimalPlaces() {
        return decimalPlaces;
    }
}
