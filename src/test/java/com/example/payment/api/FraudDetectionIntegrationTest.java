package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for fraud detection.
 * Tests velocity, high-value, and duplicate transaction detection.
 */
public class FraudDetectionIntegrationTest extends TestKitSupport {

    @Test
    public void shouldAllowNormalPayments() {
        String customerId = "cust_normal_" + System.currentTimeMillis();

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-NORMAL-001",
            new PaymentEndpoint.CustomerRequest(customerId, "normal@test.com", "Normal User"),
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
    public void shouldDetectVelocityExceeded() {
        String customerId = "cust_velocity_" + System.currentTimeMillis();

        // Make 5 payments quickly (at velocity limit)
        for (int i = 0; i < 5; i++) {
            var request = new PaymentEndpoint.CreatePaymentRequest(
                new PaymentEndpoint.MoneyRequest("50.00", "USD"),
                "tok_visa",
                null,
                "ORDER-VEL-" + i,
                new PaymentEndpoint.CustomerRequest(customerId, "velocity@test.com", "Velocity User"),
                false,
                null
            );

            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke();
        }

        // 6th payment should be blocked
        var finalRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-VEL-6",
            new PaymentEndpoint.CustomerRequest(customerId, "velocity@test.com", "Velocity User"),
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
         .hasMessageContaining("VELOCITY_EXCEEDED");
    }

    @Test
    public void shouldDetectHighValueThreshold() {
        String customerId = "cust_highval_" + System.currentTimeMillis();

        // Make payments totaling $5,500 (exceeds $5000 limit)
        var request1 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("3000.00", "USD"),
            "tok_visa",
            null,
            "ORDER-HV-1",
            new PaymentEndpoint.CustomerRequest(customerId, "highval@test.com", "High Value User"),
            false,
            null
        );

        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("2000.00", "USD"),
            "tok_visa",
            null,
            "ORDER-HV-2",
            new PaymentEndpoint.CustomerRequest(customerId, "highval@test.com", "High Value User"),
            false,
            null
        );

        var request3 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("600.00", "USD"),
            "tok_visa",
            null,
            "ORDER-HV-3",
            new PaymentEndpoint.CustomerRequest(customerId, "highval@test.com", "High Value User"),
            false,
            null
        );

        // First two should pass
        httpClient.POST("/payment/transactions")
            .withRequestBody(request1)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        httpClient.POST("/payment/transactions")
            .withRequestBody(request2)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // Third should be blocked (total would be $5600)
        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request3)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("HIGH_VALUE");
    }

    @Test
    public void shouldDetectDuplicateTransaction() {
        String customerId = "cust_duplicate_" + System.currentTimeMillis();

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("250.00", "USD"),
            "tok_visa",
            null,
            "ORDER-DUP-SAME",
            new PaymentEndpoint.CustomerRequest(customerId, "duplicate@test.com", "Duplicate User"),
            false,
            null
        );

        // First payment should succeed
        var response1 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response1.status().isSuccess()).isTrue();

        // Immediate duplicate with same amount and merchant ref should be blocked
        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("DUPLICATE_TRANSACTION");
    }

    @Test
    public void shouldAllowDifferentAmountSameMerchant() {
        String customerId = "cust_diff_amt_" + System.currentTimeMillis();

        var request1 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-SAME-MERCHANT",
            new PaymentEndpoint.CustomerRequest(customerId, "diffamt@test.com", "Diff Amount User"),
            false,
            null
        );

        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("200.00", "USD"),  // Different amount
            "tok_visa",
            null,
            "ORDER-SAME-MERCHANT",  // Same merchant ref
            new PaymentEndpoint.CustomerRequest(customerId, "diffamt@test.com", "Diff Amount User"),
            false,
            null
        );

        // Both should pass (different amounts)
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
    public void shouldAllowSameAmountDifferentMerchant() {
        String customerId = "cust_diff_merchant_" + System.currentTimeMillis();

        var request1 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("150.00", "USD"),
            "tok_visa",
            null,
            "ORDER-MERCHANT-A",
            new PaymentEndpoint.CustomerRequest(customerId, "diffmerch@test.com", "Diff Merchant User"),
            false,
            null
        );

        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("150.00", "USD"),  // Same amount
            "tok_visa",
            null,
            "ORDER-MERCHANT-B",  // Different merchant
            new PaymentEndpoint.CustomerRequest(customerId, "diffmerch@test.com", "Diff Merchant User"),
            false,
            null
        );

        // Both should pass (different merchant references)
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
}
