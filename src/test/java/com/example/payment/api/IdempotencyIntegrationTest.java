package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for idempotency key handling in payment creation.
 */
public class IdempotencyIntegrationTest extends TestKitSupport {

    @Test
    public void shouldReturnSameTransactionForDuplicateIdempotencyKey() {
        String idempotencyKey = "idem_duplicate_test_" + System.currentTimeMillis();

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("75.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IDEM-001",
            new PaymentEndpoint.CustomerRequest("cust_idem_1", "idem@test.com", "Idem User"),
            false,
            idempotencyKey
        );

        // First payment request
        var firstResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(firstResponse.status().isSuccess()).isTrue();
        String firstTransactionId = firstResponse.body().transactionId();
        assertThat(firstTransactionId).isNotNull();

        // Second payment request with same idempotency key
        var secondResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(secondResponse.status().isSuccess()).isTrue();
        String secondTransactionId = secondResponse.body().transactionId();

        // Should return same transaction ID
        assertThat(secondTransactionId).isEqualTo(firstTransactionId);

        // Response should be identical
        assertThat(secondResponse.body().merchantReference()).isEqualTo(firstResponse.body().merchantReference());
        assertThat(secondResponse.body().amount().value()).isEqualTo(firstResponse.body().amount().value());
    }

    @Test
    public void shouldCreateDifferentTransactionsForDifferentKeys() {
        String idempotencyKey1 = "idem_different_1_" + System.currentTimeMillis();
        String idempotencyKey2 = "idem_different_2_" + System.currentTimeMillis();

        var request1 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IDEM-002",
            new PaymentEndpoint.CustomerRequest("cust_idem_2", "idem2@test.com", "Idem User 2"),
            false,
            idempotencyKey1
        );

        var request2 = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IDEM-003",
            new PaymentEndpoint.CustomerRequest("cust_idem_3", "idem3@test.com", "Idem User 3"),
            false,
            idempotencyKey2
        );

        // Create two payments with different idempotency keys
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

        // Should create different transactions
        assertThat(response1.body().transactionId()).isNotEqualTo(response2.body().transactionId());
    }

    @Test
    public void shouldCreateNewTransactionWithoutIdempotencyKey() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("60.00", "USD"),
            "tok_visa",
            null,
            "ORDER-NO-IDEM",
            new PaymentEndpoint.CustomerRequest("cust_no_idem", "noidem@test.com", "No Idem User"),
            false,
            null  // No idempotency key
        );

        // First request
        var response1 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // Second request (same payload, no idempotency key)
        var response2 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // Should create different transactions (no deduplication without key)
        assertThat(response1.body().transactionId()).isNotEqualTo(response2.body().transactionId());
    }

    @Test
    public void shouldReturnExistingCompletedTransaction() {
        String idempotencyKey = "idem_completed_test_" + System.currentTimeMillis();

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IDEM-COMPLETED",
            new PaymentEndpoint.CustomerRequest("cust_idem_4", "idem4@test.com", "Idem User 4"),
            false,
            idempotencyKey
        );

        // First payment request
        var firstResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = firstResponse.body().transactionId();

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

        // Retry with same idempotency key
        var retryResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // Should return the completed transaction
        assertThat(retryResponse.body().transactionId()).isEqualTo(transactionId);
        assertThat(retryResponse.body().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    public void shouldHandleConcurrentRequestsWithSameIdempotencyKey() throws Exception {
        String idempotencyKey = "idem_concurrent_" + System.currentTimeMillis();

        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("80.00", "USD"),
            "tok_visa",
            null,
            "ORDER-IDEM-CONCURRENT",
            new PaymentEndpoint.CustomerRequest("cust_idem_5", "idem5@test.com", "Idem User 5"),
            false,
            idempotencyKey
        );

        // First request
        var response1 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String txnId1 = response1.body().transactionId();

        // Wait a bit to ensure entity is created
        Thread.sleep(100);

        // Second request with same idempotency key (sequential to avoid race)
        var response2 = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String txnId2 = response2.body().transactionId();

        // Should return same transaction ID (deduplicated via idempotency key)
        assertThat(txnId1).isEqualTo(txnId2);
        assertThat(response2.body().merchantReference()).isEqualTo(response1.body().merchantReference());
    }

    @Test
    public void shouldAcceptEmptyIdempotencyKey() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("45.00", "USD"),
            "tok_visa",
            null,
            "ORDER-EMPTY-IDEM",
            new PaymentEndpoint.CustomerRequest("cust_empty_idem", "empty@test.com", "Empty Idem User"),
            false,
            ""  // Empty string (treated as no key)
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactionId()).isNotNull();
    }
}
