package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for payment edge cases - data handling scenarios.
 */
public class PaymentEdgeCasesIntegrationTest extends TestKitSupport {

    @Test
    public void shouldHandleVeryLongMerchantReference() {
        // Merchant reference with 255 characters (near database limit)
        String longReference = "ORDER-" + "X".repeat(249);

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            longReference,
            new PaymentEndpoint.CustomerRequest("cust_long", "long@test.com", "Long Ref User"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().merchantReference()).isEqualTo(longReference);
    }

    @Test
    public void shouldHandleSpecialCharactersInCustomerData() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-SPECIAL-CHARS",
            new PaymentEndpoint.CustomerRequest(
                "cust_special",
                "test+tag@example.com",
                "O'Brien & Sons, Inc."
            ),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldHandleVerySmallPaymentAmount() {
        // Test with minimum amount (1 cent)
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("0.01", "USD"),
            "tok_visa",
            null,
            "ORDER-MIN-AMOUNT",
            new PaymentEndpoint.CustomerRequest("cust_min", "min@test.com", "Min Amount User"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().amount().value()).isEqualTo("0.01");
    }

    @Test
    public void shouldHandleVeryLargePaymentAmount() {
        // Test with large amount (9999.99)
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("9999.99", "USD"),
            "tok_visa",
            null,
            "ORDER-MAX-AMOUNT",
            new PaymentEndpoint.CustomerRequest("cust_max", "max@test.com", "Max Amount User"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().amount().value()).isEqualTo("9999.99");
    }

    @Test
    public void shouldHandleEmptyPaymentHistoryForNewCustomer() {
        String newCustomerId = "cust_new_" + System.currentTimeMillis();

        var historyRequest = new PaymentEndpoint.PaymentHistoryRequest(
            newCustomerId,
            null,
            null,
            null
        );

        var response = httpClient
            .POST("/payment/history")
            .withRequestBody(historyRequest)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactions()).isEmpty();
    }

    @Test
    public void shouldHandleSavePaymentMethodWithVeryLongIdempotencyKey() {
        // Test idempotency key with 240 characters (under 245 limit)
        String longKey = "idem_" + "X".repeat(235);

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-LONG-IDEM",
            new PaymentEndpoint.CustomerRequest("cust_long_idem", "longidem@test.com", "Long Idem User"),
            false,
            longKey
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        // Second request with same long key should return same transaction
        var response2 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response2.body().transactionId()).isEqualTo(response.body().transactionId());
    }
}
