package com.example.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money value object with currency support.
 * Immutable, handles currency conversions and arithmetic.
 * Aligned with FR-007: Support amounts from $0.01 to $999,999.99.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (amount.compareTo(new BigDecimal("999999.99")) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum allowed ($999,999.99)");
        }
        // Scale to currency decimal places
        amount = amount.setScale(currency.decimalPlaces(), RoundingMode.HALF_UP);
    }

    /**
     * Add two money amounts. Currencies must match.
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                String.format("Cannot add different currencies: %s and %s",
                    this.currency, other.currency)
            );
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtract two money amounts. Currencies must match.
     */
    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                String.format("Cannot subtract different currencies: %s and %s",
                    this.currency, other.currency)
            );
        }
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Result cannot be negative");
        }
        return new Money(result, this.currency);
    }

    /**
     * Convert to another currency using the exchange rate.
     * @param targetCurrency Target currency
     * @param exchangeRate Rate from this currency to target currency
     */
    public Money convert(Currency targetCurrency, BigDecimal exchangeRate) {
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive");
        }
        BigDecimal converted = this.amount.multiply(exchangeRate);
        return new Money(converted, targetCurrency);
    }

    /**
     * Check if this amount is less than or equal to another.
     */
    public boolean isLessThanOrEqual(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return this.amount.compareTo(other.amount) <= 0;
    }

    /**
     * Check if this amount is greater than another.
     */
    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Check if amount is zero.
     */
    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Format as string with currency symbol.
     */
    public String format() {
        return String.format("%s%s", currency.symbol(), amount.toPlainString());
    }

    @Override
    public String toString() {
        return format();
    }
}
