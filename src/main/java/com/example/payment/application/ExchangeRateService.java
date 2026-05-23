package com.example.payment.application;

import com.example.payment.domain.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Service for currency exchange rates and conversion.
 * Uses hardcoded rates for simplicity. In production, integrate with external API.
 */
public class ExchangeRateService {

    // Base currency: USD
    private static final Map<Currency, BigDecimal> EXCHANGE_RATES = Map.of(
        Currency.USD, BigDecimal.ONE,
        Currency.EUR, new BigDecimal("0.85"),
        Currency.GBP, new BigDecimal("0.73"),
        Currency.JPY, new BigDecimal("110.0"),
        Currency.AUD, new BigDecimal("1.35")
    );

    public record ExchangeRates(
        Map<Currency, BigDecimal> rates,
        Currency baseCurrency,
        Instant timestamp
    ) {}

    public record ConversionResult(
        BigDecimal originalAmount,
        Currency fromCurrency,
        BigDecimal convertedAmount,
        Currency toCurrency,
        BigDecimal exchangeRate,
        Instant timestamp
    ) {}

    /**
     * Get current exchange rates for all supported currencies.
     */
    public ExchangeRates getExchangeRates() {
        return new ExchangeRates(
            EXCHANGE_RATES,
            Currency.USD,
            Instant.now()
        );
    }

    /**
     * Convert amount from one currency to another.
     */
    public ConversionResult convertCurrency(BigDecimal amount, Currency fromCurrency, Currency toCurrency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        BigDecimal fromRate = EXCHANGE_RATES.get(fromCurrency);
        BigDecimal toRate = EXCHANGE_RATES.get(toCurrency);

        if (fromRate == null) {
            throw new IllegalArgumentException("Unsupported source currency: " + fromCurrency);
        }
        if (toRate == null) {
            throw new IllegalArgumentException("Unsupported target currency: " + toCurrency);
        }

        // Convert to base currency (USD) then to target currency
        BigDecimal amountInUSD = amount.divide(fromRate, 2, RoundingMode.HALF_UP);
        BigDecimal convertedAmount = amountInUSD.multiply(toRate).setScale(2, RoundingMode.HALF_UP);

        // Calculate direct exchange rate
        BigDecimal exchangeRate = toRate.divide(fromRate, 4, RoundingMode.HALF_UP);

        return new ConversionResult(
            amount,
            fromCurrency,
            convertedAmount,
            toCurrency,
            exchangeRate,
            Instant.now()
        );
    }
}
