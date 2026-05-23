package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for payment timeout handling.
 * Verifies that the system handles workflow timeouts gracefully.
 */
public class PaymentTimeoutIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCompletePaymentWithinReasonableTime() {
        // Create payment
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-TIMEOUT-001",
            new PaymentEndpoint.CustomerRequest("cust_timeout_1", "timeout@test.com", "Timeout User"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = response.body().transactionId();

        // Payment should complete within 10 seconds (well under workflow timeout)
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isIn("SUCCEEDED", "FAILED");
            });

        // Verify final state
        var finalResponse = httpClient
            .GET("/payment/transactions/" + transactionId)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(finalResponse.body().completedAt()).isNotNull();
    }

    @Test
    public void shouldHandleMultipleSimultaneousPayments() {
        // Create multiple payments simultaneously to test concurrent processing
        String customerId = "cust_concurrent_" + System.currentTimeMillis();

        var request1 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("30.00", "USD"),
            "tok_visa",
            null,
            "ORDER-CONCURRENT-001",
            new PaymentEndpoint.CustomerRequest(customerId, "concurrent@test.com", "Concurrent User"),
            false,
            null
        );

        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("40.00", "USD"),
            "tok_visa",
            null,
            "ORDER-CONCURRENT-002",
            new PaymentEndpoint.CustomerRequest(customerId, "concurrent@test.com", "Concurrent User"),
            false,
            null
        );

        var request3 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-CONCURRENT-003",
            new PaymentEndpoint.CustomerRequest(customerId, "concurrent@test.com", "Concurrent User"),
            false,
            null
        );

        // Submit all three payments
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

        var response3 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request3)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // All should succeed
        assertThat(response1.status().isSuccess()).isTrue();
        assertThat(response2.status().isSuccess()).isTrue();
        assertThat(response3.status().isSuccess()).isTrue();

        // All should have different transaction IDs
        assertThat(response1.body().transactionId()).isNotEqualTo(response2.body().transactionId());
        assertThat(response2.body().transactionId()).isNotEqualTo(response3.body().transactionId());

        // Wait for all to complete
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var status1 = httpClient
                    .GET("/payment/transactions/" + response1.body().transactionId())
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                var status2 = httpClient
                    .GET("/payment/transactions/" + response2.body().transactionId())
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                var status3 = httpClient
                    .GET("/payment/transactions/" + response3.body().transactionId())
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();

                assertThat(status1.body().status()).isEqualTo("SUCCEEDED");
                assertThat(status2.body().status()).isEqualTo("SUCCEEDED");
                assertThat(status3.body().status()).isEqualTo("SUCCEEDED");
            });
    }

    @Test
    public void shouldHandlePaymentHistoryDuringProcessing() {
        String customerId = "cust_history_timing_" + System.currentTimeMillis();

        // Create payment
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("75.00", "USD"),
            "tok_visa",
            null,
            "ORDER-HISTORY-TIMING",
            new PaymentEndpoint.CustomerRequest(customerId, "history@test.com", "History User"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = response.body().transactionId();

        // Query history immediately (payment still processing)
        var historyRequest = new PaymentEndpoint.PaymentHistoryRequest(
            customerId,
            null,
            null,
            null
        );

        // Should not crash even if payment is still processing
        var historyResponse = httpClient
            .POST("/payment/history")
            .withRequestBody(historyRequest)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(historyResponse.status().isSuccess()).isTrue();

        // Wait for payment to complete
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

        // History should now show completed payment (wait for view to update)
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var finalHistory = httpClient
                    .POST("/payment/history")
                    .withRequestBody(historyRequest)
                    .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
                    .invoke();
                assertThat(finalHistory.body().transactions()).isNotEmpty();
            });
    }

    @Test
    public void shouldHandleRefundDuringPaymentProcessing() {
        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-REFUND-TIMING",
            new PaymentEndpoint.CustomerRequest("cust_refund_timing", "refund-timing@test.com", "Refund Timing User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for payment to complete
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

        // Immediately initiate refund
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "Test refund timing"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Refund should be accepted
        assertThat(refundResponse.status().isSuccess()).isTrue();
        assertThat(refundResponse.body().status()).isIn("PENDING", "SUCCEEDED");
    }
}
