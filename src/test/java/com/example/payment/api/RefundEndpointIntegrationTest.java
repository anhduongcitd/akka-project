package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for refund endpoints.
 * Tests POST /payment/transactions/{id}/refunds and GET /payment/transactions/{id}/refunds.
 */
public class RefundEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldInitiateFullRefund() {
        // First, create a successful payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-REFUND-001",
            new PaymentEndpoint.CustomerRequest("cust_refund_1", "refund@test.com", "Refund User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(paymentResponse.status().isSuccess()).isTrue();
        String transactionId = paymentResponse.body().transactionId();

        // Wait for payment to succeed
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Initiate full refund
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "Customer requested full refund"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        assertThat(refundResponse.status().isSuccess()).isTrue();
        assertThat(refundResponse.body().refundId()).isNotEmpty();
        assertThat(refundResponse.body().transactionId()).isEqualTo(transactionId);
        assertThat(refundResponse.body().amount().value()).isEqualTo("100.00");
        assertThat(refundResponse.body().reason()).isEqualTo("Customer requested full refund");
    }

    @Test
    public void shouldInitiatePartialRefund() {
        // Create a payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("200.00", "USD"),
            "tok_visa",
            null,
            "ORDER-PARTIAL-001",
            new PaymentEndpoint.CustomerRequest("cust_refund_2", "partial@test.com", "Partial User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for success
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Initiate partial refund (50% of original)
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "Partial refund for one item"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        assertThat(refundResponse.status().isSuccess()).isTrue();
        assertThat(refundResponse.body().amount().value()).isEqualTo("100.00");
        assertThat(refundResponse.body().reason()).isEqualTo("Partial refund for one item");
    }

    @Test
    public void shouldListRefunds() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("300.00", "USD"),
            "tok_visa",
            null,
            "ORDER-MULTI-REFUND",
            new PaymentEndpoint.CustomerRequest("cust_refund_3", "multi@test.com", "Multi User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for success
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Create first refund
        var refund1 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "First partial refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund1)
            .invoke();

        // Create second refund
        var refund2 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "Second partial refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund2)
            .invoke();

        // Wait for refunds to be processed
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundListResponse = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                assertThat(refundListResponse.body().refunds()).hasSize(2);
            });

        // Verify refund list
        var refundListResponse = httpClient
            .GET("/payment/transactions/" + transactionId + "/refunds")
            .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
            .invoke();

        assertThat(refundListResponse.status().isSuccess()).isTrue();
        assertThat(refundListResponse.body().refunds()).hasSize(2);

        // Verify refund amounts
        var refunds = refundListResponse.body().refunds();
        assertThat(refunds).extracting("amount").extracting("value")
            .containsExactlyInAnyOrder("100.00", "50.00");
    }

    @Test
    public void shouldReturnEmptyListForNoRefunds() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("75.00", "USD"),
            "tok_visa",
            null,
            "ORDER-NO-REFUND",
            new PaymentEndpoint.CustomerRequest("cust_refund_4", "norefund@test.com", "No Refund User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for success
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Get refund list (should be empty)
        var refundListResponse = httpClient
            .GET("/payment/transactions/" + transactionId + "/refunds")
            .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
            .invoke();

        assertThat(refundListResponse.status().isSuccess()).isTrue();
        assertThat(refundListResponse.body().refunds()).isEmpty();
    }

    @Test
    public void shouldHandleRefundWithDefaultReason() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "EUR"),
            "tok_visa",
            null,
            "ORDER-DEFAULT-REASON",
            new PaymentEndpoint.CustomerRequest("cust_refund_5", "default@test.com", "Default User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for success
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Refund without reason (null reason)
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "EUR"),
            null
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        assertThat(refundResponse.status().isSuccess()).isTrue();
        // Should use default reason
        assertThat(refundResponse.body().reason()).isEqualTo("Customer requested refund");
    }

    @Test
    public void shouldRejectRefundWithInvalidAmount() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-INVALID-REFUND",
            new PaymentEndpoint.CustomerRequest("cust_refund_6", "invalid@test.com", "Invalid User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for success
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Try to refund with null amount
        var refundRequest = new PaymentEndpoint.RefundRequest(null, "Invalid refund");

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .invoke();

        // Should fail validation
        assertThat(refundResponse.status().isFailure()).isTrue();
    }
}
