package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for complete refund flow.
 * Tests: payment creation → payment success → refund initiation → refund completion.
 */
public class RefundFlowIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCompleteFullRefundFlow() {
        // Step 1: Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("150.00", "USD"),
            "tok_visa",
            null,
            "ORDER-FULL-FLOW-001",
            new PaymentEndpoint.CustomerRequest("cust_flow_1", "flow@test.com", "Flow User"),
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

        // Step 2: Wait for payment to succeed
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

        // Step 3: Initiate refund
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("150.00", "USD"),
            "Complete refund flow test"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        assertThat(refundResponse.status().isSuccess()).isTrue();
        String refundId = refundResponse.body().refundId();

        // Step 4: Verify refund appears in transaction's refund list
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundListResponse = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                assertThat(refundListResponse.body().refunds()).isNotEmpty();
                var refund = refundListResponse.body().refunds().get(0);
                assertThat(refund.refundId()).isEqualTo(refundId);
                assertThat(refund.amount().value()).isEqualTo("150.00");
            });

        // Step 5: Verify transaction shows refund in its state
        var finalTransactionResponse = httpClient
            .GET("/payment/transactions/" + transactionId)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // Transaction should still be SUCCEEDED or show REFUNDED status
        assertThat(finalTransactionResponse.body().status()).isIn("SUCCEEDED", "REFUNDED");
    }

    @Test
    public void shouldHandleMultiplePartialRefunds() {
        // Create payment for $200
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("200.00", "USD"),
            "tok_visa",
            null,
            "ORDER-MULTI-REFUND-001",
            new PaymentEndpoint.CustomerRequest("cust_flow_2", "multirefund@test.com", "Multi Refund User"),
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

        // First partial refund: $80
        var refund1 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("80.00", "USD"),
            "First partial refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund1)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Second partial refund: $60
        var refund2 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("60.00", "USD"),
            "Second partial refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund2)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Third partial refund: $40
        var refund3 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("40.00", "USD"),
            "Third partial refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund3)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Wait for all refunds to be processed
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundListResponse = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                assertThat(refundListResponse.body().refunds()).hasSize(3);
            });

        // Verify all refunds
        var refundListResponse = httpClient
            .GET("/payment/transactions/" + transactionId + "/refunds")
            .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
            .invoke();

        assertThat(refundListResponse.body().refunds()).hasSize(3);

        // Verify total refunded amount = $180 (original was $200, so $20 net)
        var totalRefunded = refundListResponse.body().refunds().stream()
            .map(r -> Double.parseDouble(r.amount().value()))
            .reduce(0.0, Double::sum);

        assertThat(totalRefunded).isEqualTo(180.0);
    }

    @Test
    public void shouldHandleRefundOfDifferentCurrency() {
        // Create EUR payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "EUR"),
            "tok_visa",
            null,
            "ORDER-EUR-REFUND-001",
            new PaymentEndpoint.CustomerRequest("cust_flow_3", "eur@test.com", "EUR User"),
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

        // Refund in EUR
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "EUR"),
            "Partial EUR refund"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        assertThat(refundResponse.status().isSuccess()).isTrue();
        assertThat(refundResponse.body().amount().currency()).isEqualTo("EUR");
        assertThat(refundResponse.body().amount().value()).isEqualTo("50.00");
    }

    @Test
    public void shouldPreserveRefundReasonsThroughoutFlow() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("120.00", "GBP"),
            "tok_visa",
            null,
            "ORDER-REASON-TEST",
            new PaymentEndpoint.CustomerRequest("cust_flow_4", "reason@test.com", "Reason User"),
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

        // Refund with specific reason
        String refundReason = "Product arrived damaged - customer complaint #12345";
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("120.00", "GBP"),
            refundReason
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Verify reason preserved in initial response
        assertThat(refundResponse.body().reason()).isEqualTo(refundReason);

        // Verify reason preserved in refund list
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundListResponse = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                assertThat(refundListResponse.body().refunds()).isNotEmpty();
                assertThat(refundListResponse.body().refunds().get(0).reason()).isEqualTo(refundReason);
            });
    }

    @Test
    public void shouldHandleRefundOnRecentlySucceededPayment() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("90.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IMMEDIATE-REFUND",
            new PaymentEndpoint.CustomerRequest("cust_flow_5", "immediate@test.com", "Immediate User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for success (minimal wait)
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

        // Immediately initiate refund after success
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("90.00", "USD"),
            "Immediate refund after payment success"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Should succeed without issues
        assertThat(refundResponse.status().isSuccess()).isTrue();
        assertThat(refundResponse.body().status()).isIn("PENDING", "SUCCEEDED");
    }
}
