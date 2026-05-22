package com.example.payment.application;

import com.example.payment.domain.Currency;
import com.typesafe.config.Config;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exchange rate service with caching.
 * Fetches real-time exchange rates from external API.
 * Aligned with FR-019: Real-time exchange rates for currency conversions.
 */
public class ExchangeRateService {

    private final String apiUrl;
    private final HttpClient httpClient;
    private final Map<String, CachedRate> rateCache;
    private static final Duration CACHE_DURATION = Duration.ofMinutes(15);

    private record CachedRate(Map<Currency, BigDecimal> rates, Instant fetchedAt) {
        boolean isExpired() {
            return Duration.between(fetchedAt, Instant.now()).compareTo(CACHE_DURATION) > 0;
        }
    }

    public ExchangeRateService(Config config) {
        this.apiUrl = config.getString("payment.exchange-rate.api-url");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.rateCache = new ConcurrentHashMap<>();
    }

    /**
     * Get exchange rate from base currency to target currency.
     * Uses caching to reduce API calls.
     */
    public CompletableFuture<BigDecimal> getRate(Currency baseCurrency, Currency targetCurrency) {
        if (baseCurrency == targetCurrency) {
            return CompletableFuture.completedFuture(BigDecimal.ONE);
        }

        String cacheKey = baseCurrency.name();
        CachedRate cached = rateCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            BigDecimal rate = cached.rates.get(targetCurrency);
            if (rate != null) {
                return CompletableFuture.completedFuture(rate);
            }
        }

        return fetchRatesFromApi(baseCurrency).thenApply(rates -> {
            BigDecimal rate = rates.get(targetCurrency);
            if (rate == null) {
                throw new RuntimeException(
                    String.format("Exchange rate not available for %s to %s",
                        baseCurrency, targetCurrency)
                );
            }
            return rate;
        });
    }

    /**
     * Fetch latest rates from API and cache them.
     */
    private CompletableFuture<Map<Currency, BigDecimal>> fetchRatesFromApi(Currency baseCurrency) {
        String url = apiUrl.replace("{currency}", baseCurrency.name());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new RuntimeException(
                        "Failed to fetch exchange rates: " + response.statusCode()
                    );
                }
                return parseRates(response.body());
            })
            .thenApply(rates -> {
                rateCache.put(baseCurrency.name(), new CachedRate(rates, Instant.now()));
                return rates;
            });
    }

    /**
     * Parse API response to extract exchange rates.
     * Expected format: {"rates": {"USD": 1.0, "EUR": 0.85, ...}}
     */
    private Map<Currency, BigDecimal> parseRates(String jsonResponse) {
        Map<Currency, BigDecimal> rates = new ConcurrentHashMap<>();

        try {
            // Simple JSON parsing (in production, use a proper JSON library)
            String ratesSection = jsonResponse.substring(
                jsonResponse.indexOf("\"rates\":") + 8
            );
            ratesSection = ratesSection.substring(1, ratesSection.indexOf("}"));

            for (String pair : ratesSection.split(",")) {
                String[] parts = pair.replace("\"", "").split(":");
                String currencyCode = parts[0].trim();
                String rateValue = parts[1].trim();

                try {
                    Currency currency = Currency.valueOf(currencyCode);
                    rates.put(currency, new BigDecimal(rateValue));
                } catch (IllegalArgumentException e) {
                    // Currency not supported, skip
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse exchange rates: " + e.getMessage(), e);
        }

        return rates;
    }

    /**
     * Clear cache (useful for testing).
     */
    public void clearCache() {
        rateCache.clear();
    }
}
