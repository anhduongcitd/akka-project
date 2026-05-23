package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment Endpoint Integration Test.
 * Tests REST API endpoints with TestKit.
 */
public class PaymentEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreatePaymentRequest() {
        // Test request structure
        var moneyRequest = new PaymentEndpoint.MoneyRequest("100.00", "USD");
        var customerRequest = new PaymentEndpoint.CustomerRequest(
            "cust_123",
            "test@example.com",
            "Test Customer"
        );

        var request = new PaymentEndpoint.CreatePaymentRequest(
            moneyRequest,
            "tok_visa",
            null,  // paymentMethodId
            "ORDER-001",
            customerRequest,
            false
        );

        assertThat(request.amount().value()).isEqualTo("100.00");
        assertThat(request.customer().email()).isEqualTo("test@example.com");
    }

    @Test
    public void shouldInitiateRefund() {
        // Create a payment first
        var createRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,  // paymentMethodId
            "ORDER-REFUND-001",
            new PaymentEndpoint.CustomerRequest("cust_refund", "refund@test.com", "Refund Customer"),
            false
        );

        var createResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(createRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();
        String transactionId = createResponse.body().transactionId();
        assertThat(transactionId).isNotNull();

        // Wait for payment to complete
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var txn = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(txn.body().status()).isIn("SUCCEEDED", "FAILED");
            });

        // Initiate refund
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "Customer requested partial refund"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        assertThat(refundResponse.status().isSuccess()).isTrue();
        assertThat(refundResponse.body().refundId()).isNotNull();
        assertThat(refundResponse.body().transactionId()).isEqualTo(transactionId);
        assertThat(refundResponse.body().status()).isEqualTo("PENDING");
        assertThat(refundResponse.body().amount().value()).isEqualTo("50.00");
    }

    @Test
    public void shouldGetRefundsList() {
        // Create a payment first
        var createRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("200.00", "USD"),
            "tok_visa",
            null,  // paymentMethodId
            "ORDER-REFUND-LIST-001",
            new PaymentEndpoint.CustomerRequest("cust_list", "list@test.com", "List Customer"),
            false
        );

        var createResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(createRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();
        String transactionId = createResponse.body().transactionId();

        // Wait for payment to complete
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var txn = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(txn.body().status()).isIn("SUCCEEDED", "FAILED");
            });

        // Initiate two refunds
        var refund1 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "First refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund1)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        var refund2 = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("30.00", "USD"),
            "Second refund"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refund2)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Get refunds list
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundsList = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                assertThat(refundsList.status().isSuccess()).isTrue();
                assertThat(refundsList.body().refunds()).hasSize(2);
                assertThat(refundsList.body().refunds())
                    .extracting("amount.value")
                    .containsExactlyInAnyOrder("50.00", "30.00");
            });
    }

    @Test
    public void shouldRejectRefundExceedingOriginalAmount() {
        // Create a payment first
        var createRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,  // paymentMethodId
            "ORDER-EXCEED-001",
            new PaymentEndpoint.CustomerRequest("cust_exceed", "exceed@test.com", "Exceed Customer"),
            false
        );

        var createResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(createRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();
        String transactionId = createResponse.body().transactionId();

        // Wait for payment to complete
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var txn = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(txn.body().status()).isIn("SUCCEEDED", "FAILED");
            });

        // Try to refund more than the original amount
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("150.00", "USD"),
            "Attempt to over-refund"
        );

        var refundResponse = httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Refund is initiated but should fail in workflow
        assertThat(refundResponse.status().isSuccess()).isTrue();
        String refundId = refundResponse.body().refundId();

        // Wait and verify the refund failed
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundsList = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                var failedRefund = refundsList.body().refunds().stream()
                    .filter(r -> r.refundId().equals(refundId))
                    .findFirst();

                assertThat(failedRefund).isPresent();
                assertThat(failedRefund.get().status()).isEqualTo("FAILED");
            });
    }

    @Test
    public void shouldHandleMultiplePartialRefunds() {
        // Create a payment first
        var createRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("500.00", "USD"),
            "tok_visa",
            null,  // paymentMethodId
            "ORDER-PARTIAL-001",
            new PaymentEndpoint.CustomerRequest("cust_partial", "partial@test.com", "Partial Customer"),
            false
        );

        var createResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(createRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();
        String transactionId = createResponse.body().transactionId();

        // Wait for payment to complete
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var txn = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(txn.body().status()).isIn("SUCCEEDED", "FAILED");
            });

        // Multiple partial refunds totaling original amount
        var refunds = new String[]{"100.00", "150.00", "200.00", "50.00"};

        for (String amount : refunds) {
            var refundRequest = new PaymentEndpoint.RefundRequest(
                new PaymentEndpoint.MoneyRequest(amount, "USD"),
                "Partial refund of " + amount
            );

            var refundResponse = httpClient
                .POST("/payment/transactions/" + transactionId + "/refunds")
                .withRequestBody(refundRequest)
                .responseBodyAs(PaymentEndpoint.RefundResponse.class)
                .invoke();

            assertThat(refundResponse.status().isSuccess()).isTrue();
        }

        // Verify all refunds are processed
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var refundsList = httpClient
                    .GET("/payment/transactions/" + transactionId + "/refunds")
                    .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
                    .invoke();

                assertThat(refundsList.body().refunds()).hasSize(4);
                assertThat(refundsList.body().refunds())
                    .allMatch(r -> r.status().equals("SUCCEEDED") || r.status().equals("PENDING") || r.status().equals("FAILED"));
            });
    }
}
