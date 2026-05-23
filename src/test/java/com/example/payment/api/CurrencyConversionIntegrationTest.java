package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for currency exchange rates and conversion endpoints.
 */
public class CurrencyConversionIntegrationTest extends TestKitSupport {

    @Test
    public void shouldGetExchangeRates() {
        var response = httpClient
            .GET("/payment/exchange-rates")
            .responseBodyAs(PaymentEndpoint.ExchangeRatesResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().rates()).isNotEmpty();
        assertThat(response.body().rates()).containsKeys("USD", "EUR", "GBP", "JPY", "AUD");
        assertThat(response.body().baseCurrency()).isEqualTo("USD");
        assertThat(response.body().timestamp()).isNotNull();

        // Verify USD rate is 1.0
        assertThat(response.body().rates().get("USD")).isEqualTo("1");
    }

    @Test
    public void shouldConvertCurrency() {
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "100.00",
            "USD",
            "EUR"
        );

        var response = httpClient
            .POST("/payment/convert")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        // Original amount
        assertThat(response.body().originalAmount().value()).isEqualTo("100.00");
        assertThat(response.body().originalAmount().currency()).isEqualTo("USD");

        // Converted amount (100 USD * 0.85 = 85 EUR)
        assertThat(response.body().convertedAmount().value()).isEqualTo("85.00");
        assertThat(response.body().convertedAmount().currency()).isEqualTo("EUR");

        // Exchange rate
        assertThat(response.body().exchangeRate()).isEqualTo("0.8500");
        assertThat(response.body().timestamp()).isNotNull();
    }

    @Test
    public void shouldConvertBetweenNonUSDCurrencies() {
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "100.00",
            "EUR",
            "GBP"
        );

        var response = httpClient
            .POST("/payment/convert")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        // Original amount
        assertThat(response.body().originalAmount().value()).isEqualTo("100.00");
        assertThat(response.body().originalAmount().currency()).isEqualTo("EUR");

        // Converted amount (100 EUR / 0.85 * 0.73 = 85.88 GBP)
        assertThat(response.body().convertedAmount().value()).isEqualTo("85.88");
        assertThat(response.body().convertedAmount().currency()).isEqualTo("GBP");

        assertThat(response.body().timestamp()).isNotNull();
    }

    @Test
    public void shouldConvertToJPY() {
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "100.00",
            "USD",
            "JPY"
        );

        var response = httpClient
            .POST("/payment/convert")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        // Original amount
        assertThat(response.body().originalAmount().value()).isEqualTo("100.00");
        assertThat(response.body().originalAmount().currency()).isEqualTo("USD");

        // Converted amount (100 USD * 110 = 11000 JPY)
        // Note: BigDecimal strips trailing zeros, so "11000.00" becomes "11000"
        assertThat(response.body().convertedAmount().value()).isEqualTo("11000");
        assertThat(response.body().convertedAmount().currency()).isEqualTo("JPY");

        assertThat(response.body().exchangeRate()).isEqualTo("110.0000");
    }

    @Test
    public void shouldRejectInvalidAmount() {
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "-100.00",
            "USD",
            "EUR"
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/convert")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Amount must be positive");
    }

    @Test
    public void shouldRejectInvalidCurrency() {
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "100.00",
            "USD",
            "INVALID"
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/convert")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("INVALID");
    }

    @Test
    public void shouldHandleSameCurrencyConversion() {
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "100.00",
            "USD",
            "USD"
        );

        var response = httpClient
            .POST("/payment/convert")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        // Same currency should return same amount
        assertThat(response.body().originalAmount().value()).isEqualTo("100.00");
        assertThat(response.body().convertedAmount().value()).isEqualTo("100.00");
        assertThat(response.body().exchangeRate()).isEqualTo("1.0000");
    }
}
