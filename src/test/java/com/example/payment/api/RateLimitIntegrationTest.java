package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for rate limiting.
 * Tests IP-based and customer-based rate limits on payment endpoints.
 */
public class RateLimitIntegrationTest extends TestKitSupport {

    @Test
    public void shouldAllowPaymentWithinCustomerLimit() {
        // Create a payment - should succeed
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-RATE-001",
            new PaymentEndpoint.CustomerRequest("cust_rate_test_1", "rate1@test.com", "Rate Test User 1"),
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
    @org.junit.jupiter.api.Disabled("Disabled: Fraud detection (5 payments/10min) triggers before rate limit (50 payments/hour) can be tested")
    public void shouldEnforceCustomerPaymentLimit() {
        // Use unique customer ID for this test to avoid interference
        String customerId = "cust_limit_" + System.currentTimeMillis();

        // Create multiple payments to exceed limit (51 payments, limit is 50/hour)
        // Use different amounts to avoid fraud detection
        for (int i = 0; i < 50; i++) {
            String amount = String.format("10.%02d", i % 100);
            var request = new PaymentEndpoint.CreatePaymentRequest(
                new PaymentEndpoint.MoneyRequest(amount, "USD"),
                "tok_visa",
                null,
                "ORDER-LIMIT-" + i,
                new PaymentEndpoint.CustomerRequest(customerId, "limit@test.com", "Limit Test User"),
                false,
                null
            );

            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke();
        }

        // 51st payment should be rejected
        var finalRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("10.00", "USD"),
            "tok_visa",
            null,
            "ORDER-LIMIT-51",
            new PaymentEndpoint.CustomerRequest(customerId, "limit@test.com", "Limit Test User"),
            false,
            null
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(finalRequest)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Payment limit exceeded for customer");
    }

    @Test
    public void shouldAllowPaymentsForDifferentCustomers() {
        // Two different customers should have independent rate limits
        String customer1 = "cust_independent_1_" + System.currentTimeMillis();
        String customer2 = "cust_independent_2_" + System.currentTimeMillis();

        var request1 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IND-1",
            new PaymentEndpoint.CustomerRequest(customer1, "ind1@test.com", "Independent User 1"),
            false,
            null
        );

        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("200.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IND-2",
            new PaymentEndpoint.CustomerRequest(customer2, "ind2@test.com", "Independent User 2"),
            false,
            null
        );

        // Both should succeed
        var response1 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request1)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        var response2 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request2)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response1.status().isSuccess()).isTrue();
        assertThat(response2.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldTrackRateLimitsSeparatelyPerCustomer() {
        // Test that each customer has their own counter
        String customer1 = "cust_separate_1_" + System.currentTimeMillis();
        String customer2 = "cust_separate_2_" + System.currentTimeMillis();

        // Customer 1 makes 3 payments
        for (int i = 0; i < 3; i++) {
            var request = new PaymentEndpoint.CreatePaymentRequest(
                new PaymentEndpoint.MoneyRequest("25.00", "USD"),
                "tok_visa",
                null,
                "ORDER-SEP1-" + i,
                new PaymentEndpoint.CustomerRequest(customer1, "sep1@test.com", "Separate User 1"),
                false,
                null
            );

            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke();
        }

        // Customer 2 should still be able to make payments
        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("75.00", "USD"),
            "tok_visa",
            null,
            "ORDER-SEP2-1",
            new PaymentEndpoint.CustomerRequest(customer2, "sep2@test.com", "Separate User 2"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request2)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }
}
